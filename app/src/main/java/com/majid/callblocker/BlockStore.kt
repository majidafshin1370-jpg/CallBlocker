package com.majid.callblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Blocked(val name: String, val number: String)

object BlockStore {
    private const val PREFS = "callblocker"
    private const val KEY_LIST = "list"
    private const val KEY_ENABLED = "enabled"

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_ENABLED, true)

    fun setEnabled(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun load(c: Context): List<Blocked> {
        val raw = prefs(c).getString(KEY_LIST, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Blocked(o.getString("n"), o.getString("p"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(c: Context, list: List<Blocked>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("p", it.number)) }
        prefs(c).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    /** Returns false if the number was already in the list or is empty. */
    fun add(c: Context, name: String, number: String): Boolean {
        val k = key(number)
        if (k.isEmpty()) return false
        val list = load(c)
        if (list.any { key(it.number) == k }) return false
        val shownName = name.ifBlank { number }
        save(c, list + Blocked(shownName, number.trim()))
        return true
    }

    fun remove(c: Context, item: Blocked) {
        save(c, load(c).filterNot { key(it.number) == key(item.number) })
    }

    /**
     * Normalizes a phone number to its last 10 digits so that
     * 09121234567, +989121234567 and 00989121234567 all match.
     * Persian/Arabic digits are handled as well.
     */
    fun key(number: String): String {
        val digits = StringBuilder()
        for (ch in number) {
            val d = Character.digit(ch, 10)
            if (d >= 0) digits.append(d)
        }
        val s = digits.toString()
        return if (s.length > 10) s.takeLast(10) else s
    }

    fun isBlocked(c: Context, number: String): Boolean {
        val k = key(number)
        if (k.isEmpty()) return false
        return load(c).any { key(it.number) == k }
    }
}
