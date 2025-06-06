package com.tapbi.spark.gpsmappro.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tapbi.spark.gpsmappro.App
import org.json.JSONArray
import org.json.JSONException
import androidx.core.content.edit

object SharedPreferenceHelper {
    private const val PREF_NAME = "PREF_NAME"
    private const val DEFAULT_NUM = -1
    private const val DEFAULT_STRING = ""
    private var sharedPreferences: SharedPreferences? = null

    fun init(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(): SharedPreferences {
        if (sharedPreferences == null) {
            sharedPreferences = App.instance?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
        return sharedPreferences!!
    }

    fun putString(key: String?, value: String?) {
        getPrefs().edit { putString(key, value) }
    }

    fun getString(key: String?): String? {
        return getPrefs().getString(key, DEFAULT_STRING)
    }

    fun getString(key: String?, defaultValue: String): String {
        return getPrefs().getString(key, defaultValue).toString()
    }

    fun getStringWithDefault(key: String?, defaultValue: String): String {
        return getPrefs().getString(key, defaultValue) ?: defaultValue
    }

    fun putInt(key: String?, value: Int) {
        getPrefs().edit().putInt(key, value)?.apply()
    }

    fun getInt(key: String?): Int {
        return getPrefs().getInt(key, DEFAULT_NUM)
    }

    fun getInt(key: String?, default: Int): Int {
        return getPrefs().getInt(key, default)
    }

    fun getIntWithDefault(key: String?, defaultValue: Int): Int {
        return getPrefs().getInt(key, defaultValue)
    }

    @SuppressLint("CommitPrefEdits")
    fun putLong(key: String?, value: Long) {
        getPrefs().edit { putLong(key, value) }
    }

    fun getLong(key: String?): Long {
        return getPrefs().getLong(key, DEFAULT_NUM.toLong())
    }

    fun getLong(key: String?, default: Long): Long {
        return getPrefs().getLong(key, default)
    }

    fun putBoolean(key: String?, value: Boolean) {
        getPrefs().edit { putBoolean(key, value) }
    }

    fun getBoolean(key: String?, defaultValue: Boolean): Boolean {
        return getPrefs().getBoolean(key, defaultValue)
    }

    fun putFloat(key: String?, value: Float) {
        getPrefs().edit().putFloat(key, value)?.apply()
    }

    fun getFloat(key: String?): Float {
        return getPrefs().getFloat(key, 0f)
    }

    fun setStringArrayPref(key: String?, values: List<String?>) {
        val editor = getPrefs().edit()
        val a = JSONArray()
        for (i in values.indices) {
            a.put(values[i])
        }
        if (!values.isEmpty()) {
            editor.putString(key, a.toString())
        } else {
            editor.putString(key, DEFAULT_STRING)
        }
        editor.apply()
    }

    fun getStringArrayPref(key: String?): List<String> {
        val json = getPrefs().getString(key, null)
        val urls = ArrayList<String>()
        if (json != null) {
            try {
                val a = JSONArray(json)
                for (i in 0 until a.length()) {
                    val url = a.optString(i)
                    urls.add(url)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return urls
    }

    fun setIntArray(key: String?, values: List<Int?>) {
        val editor = getPrefs().edit()
        val a = JSONArray()
        for (i in values.indices) {
            a.put(values[i])
        }
        if (!values.isEmpty()) {
            editor.putString(key, a.toString())
        } else {
            editor.putString(key, "")
        }
        editor.apply()
    }

    fun getIntArray(key: String?): List<Int> {
        val json = getPrefs().getString(key, null)
        val urls = ArrayList<Int>()
        if (json != null) {
            try {
                val a = JSONArray(json)
                for (i in 0 until a.length()) {
                    val url = a.optString(i)
                    urls.add(Integer.valueOf(url))
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return urls
    }

    fun removeKey(key: String?) {
        getPrefs().edit().remove(key)?.apply()
    }

    fun resetAll() {
        getPrefs().edit().clear()?.apply()
    }

    fun containKey(key: String?): Boolean {
        return getPrefs().contains(key)
    }

    inline fun <reified T> putObject(key: String, obj: T) {
        val json = Gson().toJson(obj)
        putString(key, json)
    }

    inline fun <reified T> getObject(key: String): T? {
        val json = getString(key)
        return if (!json.isNullOrEmpty()) {
            try {
                Gson().fromJson(json, T::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }

    inline fun <reified T> putObjectList(key: String, list: List<T>) {
        val json = Gson().toJson(list)
        putString(key, json)
    }

    inline fun <reified T> getObjectList(key: String): List<T> {
        val json = getString(key)
        return if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<T>>() {}.type
                Gson().fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    inline fun <reified K, reified V> putObjectMap(key: String, map: Map<K, V>) {
        val json = Gson().toJson(map)
        putString(key, json)
    }
    inline fun <reified K, reified V> getObjectMap(key: String): Map<K, V> {
        val json = getString(key)
        return if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<Map<K, V>>() {}.type
                Gson().fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }

}