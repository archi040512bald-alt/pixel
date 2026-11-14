package com.example.pixellauncher

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object IconPacks {
    private var loaded = ""
    private var res: Resources? = null
    private val map = HashMap<String, String>()
    private val actions = arrayOf("org.adw.launcher.THEMES", "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME", "org.adw.launcher.icons.ACTION_PICK_ICON")

    fun find(c: Context): List<Pair<String, String>> {
        val pm = c.packageManager
        val out = LinkedHashMap<String, String>()
        for (a in actions) {
            for (ri in pm.queryIntentActivities(Intent(a), 0)) {
                out[ri.activityInfo.packageName] = ri.loadLabel(pm).toString()
            }
        }
        return out.map { Pair(it.key, it.value) }
    }

    private fun load(c: Context, pkg: String) {
        loaded = pkg
        map.clear()
        res = null
        try {
            val r = c.packageManager.getResourcesForApplication(pkg)
            res = r
            val id = r.getIdentifier("appfilter", "xml", pkg)
            val p: XmlPullParser = if (id != 0) r.getXml(id) else {
                val xp = XmlPullParserFactory.newInstance().newPullParser()
                xp.setInput(c.createPackageContext(pkg, 0).assets.open("appfilter.xml"), null)
                xp
            }
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG && p.name == "item") {
                    val comp = p.getAttributeValue(null, "component")
                    val d = p.getAttributeValue(null, "drawable")
                    if (comp != null && d != null) map[comp] = d
                }
                e = p.next()
            }
        } catch (ex: Exception) {
            res = null
        }
    }

    fun icon(c: Context, app: String, cls: String): Drawable? {
        val pack = Prefs(c).pack
        if (pack.isEmpty()) return null
        if (pack != loaded) load(c, pack)
        val r = res ?: return null
        val name = map["ComponentInfo{" + app + "/" + cls + "}"] ?: return null
        val id = r.getIdentifier(name, "drawable", pack)
        if (id == 0) return null
        return try { r.getDrawable(id, null) } catch (ex: Exception) { null }
    }
}
