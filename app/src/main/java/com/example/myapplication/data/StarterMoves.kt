package com.example.myapplication.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.model.MoveStatus

/**
 * A foundation move the library comes with. [category] is one of [AppDatabase.DEFAULT_CATEGORIES],
 * and [variationOf] names the starter move this is a variation of.
 */
data class StarterMove(
    val name: String,
    val category: String,
    val notes: String,
    val variationOf: String? = null,
    /** Other names for the move, so a library that already has it under one of them isn't given a copy. */
    val aliases: List<String> = emptyList(),
) {
    val keys: Set<String> get() = (listOf(name) + aliases).map(StarterMoves::normalize).toSet()
}

/** The basic moves of each category. They start as Learning, and the user can edit or delete any of them. */
object StarterMoves {
    val ALL: List<StarterMove> = listOf(
        StarterMove(
            "Indian step", "Toprock",
            "Cross one foot in front of the other with a small kick, step back out and switch sides, bouncing on the beat.",
        ),
        StarterMove(
            "Crossover", "Toprock",
            "Step one foot across in front of the other, open back out and cross the other way, arms opening and closing with the legs.",
            aliases = listOf("Cross step", "Crossover step"),
        ),
        StarterMove(
            "Side step", "Toprock",
            "Step out to one side and back in, then the other side, swinging your arms across your body.",
        ),
        StarterMove(
            "Salsa step", "Toprock",
            "Step one foot back on a diagonal and return, then the other side, turning your shoulders with each step.",
        ),

        StarterMove(
            "Knee drop", "Go-downs",
            "Kick one leg back and tuck its foot behind your other knee, then sink onto that foot's toes, not the knee.",
        ),
        StarterMove(
            "Sweep drop", "Go-downs",
            "Sink low as one straight leg sweeps a wide arc, catching your weight on your hands and flowing into footwork.",
        ),
        StarterMove(
            "Coin drop", "Go-downs",
            "Shift your weight onto one hand, then the forearm and shoulder as your feet leave the floor. Leads into backspins and windmills.",
        ),
        StarterMove(
            "Spin down", "Go-downs",
            "Turn on the spot as you sink, landing on your hands ready for footwork.",
        ),
        StarterMove(
            "Corkscrew", "Go-downs",
            "Cross your legs and twist down to the floor in a spiral. Reverse it to come back up.",
        ),

        StarterMove(
            "6-step", "Footwork",
            "Walk your feet in a circle around your hands in six steps, through push-up and crossed-leg positions. The base of most footwork.",
            aliases = listOf("Six step"),
        ),
        StarterMove(
            "3-step", "Footwork",
            "A shorter 6-step: three steps around your hands, often finished with a kick-out.",
            variationOf = "6-step",
            aliases = listOf("Three step"),
        ),
        StarterMove(
            "2-step", "Footwork",
            "The 6-step cut down to two steps, stepping each leg through and back.",
            variationOf = "6-step",
            aliases = listOf("Two step"),
        ),
        StarterMove(
            "CC's", "Footwork",
            "From a crouch with your hands down, switch legs back and forth, shooting one out in front while the other tucks under you.",
        ),
        StarterMove(
            "Kick-out", "Footwork",
            "From a crouch or mid-footwork, shoot your legs out straight, then snap them back under you.",
        ),
        StarterMove(
            "Zulu spin", "Footwork",
            "Crouch on one foot with the other leg stretched out, and spin around on the ball of your foot.",
        ),
        StarterMove(
            "Coffee grinder", "Footwork",
            "Crouched with a hand down, sweep one straight leg in a full circle under you, hopping the other foot over it.",
            aliases = listOf("Helicopter"),
        ),

        StarterMove(
            "Backspin", "Power",
            "Roll onto your upper back with your knees tucked, then open your legs and push off to spin.",
        ),
        StarterMove(
            "Windmill", "Power",
            "Roll continuously across your shoulders and upper back, swinging your legs in a wide V to keep turning.",
            aliases = listOf("Mill"),
        ),
        StarterMove(
            "Swipe", "Power",
            "Throw your weight onto your hands and whip your legs around through the air, landing facing the other way.",
        ),
        StarterMove(
            "Headspin", "Power",
            "Balance on your head, push off with your hands and spin with your legs up. Wear a beanie or helmet.",
        ),

        StarterMove(
            "Baby freeze", "Freezes",
            "Head and both hands down, one knee resting on the back of the same-side elbow, then lift your feet and hold.",
            aliases = listOf("Baby"),
        ),
        StarterMove(
            "Chair freeze", "Freezes",
            "Balance on one hand with that elbow dug into your side, legs crossed in the air like you're sitting in a chair.",
            aliases = listOf("Chair"),
        ),
        StarterMove(
            "Turtle freeze", "Freezes",
            "Tuck both elbows into your stomach, lean forward onto your hands and lift your feet off the floor.",
        ),
        StarterMove(
            "Elbow freeze", "Freezes",
            "Balance on one hand with that elbow in your hip, and hold your body flat in the air.",
        ),
        StarterMove(
            "Headstand", "Freezes",
            "Make a triangle with your head and hands on the floor, then lift your legs and hold. Practice on a soft surface.",
        ),
    )

    /** Lowercase letters and digits without a trailing "s", so "CC's", "ccs" and "CC" all match. */
    fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }.removeSuffix("s")

    /**
     * Adds each starter move the library doesn't already have under its name or an alias, ignoring case,
     * spaces and punctuation. A move goes in the category whose name matches its own, and is skipped if the
     * user renamed or deleted that category. A variation is filed under its base move only when that move
     * is a base move in the same category. Existing moves are never changed.
     */
    fun seed(db: SupportSQLiteDatabase, now: Long = System.currentTimeMillis()) {
        val categoryIds = mutableMapOf<String, Long>()
        db.query("SELECT id, name FROM categories ORDER BY sortOrder, id").use { row ->
            while (row.moveToNext()) categoryIds.putIfAbsent(normalize(row.getString(1)), row.getLong(0))
        }
        val known = mutableListOf<KnownMove>()
        db.query("SELECT id, name, categoryId, parentMoveId FROM moves ORDER BY id").use { row ->
            while (row.moveToNext()) {
                known += KnownMove(row.getLong(0), normalize(row.getString(1)), row.getLong(2), isBase = row.isNull(3))
            }
        }

        val byName = ALL.associateBy { it.name }
        for (starter in ALL) {
            val keys = starter.keys
            if (known.any { it.key in keys }) continue
            val categoryId = categoryIds[normalize(starter.category)] ?: continue
            val parentId = starter.variationOf?.let { byName.getValue(it).keys }?.let { parentKeys ->
                known.firstOrNull { it.key in parentKeys && it.categoryId == categoryId && it.isBase }?.id
            }
            val values = ContentValues().apply {
                put("name", starter.name)
                put("categoryId", categoryId)
                if (parentId == null) putNull("parentMoveId") else put("parentMoveId", parentId)
                put("status", MoveStatus.LEARNING.name)
                put("notes", starter.notes)
                put("createdAt", now)
            }
            val id = db.insert("moves", SQLiteDatabase.CONFLICT_ABORT, values)
            known += KnownMove(id, normalize(starter.name), categoryId, isBase = parentId == null)
        }
    }

    private class KnownMove(val id: Long, val key: String, val categoryId: Long, val isBase: Boolean)
}
