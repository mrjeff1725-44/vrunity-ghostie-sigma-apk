package com.vrunity.vrapk

import android.content.Context
import android.opengl.Matrix
import org.json.JSONArray
import org.json.JSONObject

// The scene as the game draws it: every shape with its world transform, its colour
// already in the space the shader lights in, and where the player starts.
class Scene private constructor() {
    val bg = floatArrayOf(0.06f, 0.08f, 0.14f)
    val items = ArrayList<Item>()
    var startX = 0f
    var startZ = 0f
    var startYaw = 0f
    var eyeHeight = 1.6f

    // The sky the scene is framed under, the lights that light it and the fog that
    // gives it depth — taken from the scene's own settings, so the headset shows the
    // same light the editor does instead of a flat background under a fixed lamp.
    var skyType = "flat"
    val skyTop = floatArrayOf(0.02f, 0.10f, 0.35f)
    val skyHorizon = floatArrayOf(0.42f, 0.62f, 0.85f)
    val skySunDir = floatArrayOf(0.3f, 0.8f, 0.2f)
    val skySunColor = floatArrayOf(1f, 0.95f, 0.85f)
    var skySunSize = 0.02f
    var skyStars = 0f
    var skyHaze = 0.25f
    var skyGlow = 1f
    val flatSky = floatArrayOf(0.02f, 0.02f, 0.03f)
    // At most two directional lights: the world's own light and the scene's light.
    // An unused second light points straight up with no colour, so it adds nothing.
    val sunDir = floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f)
    val sunColor = FloatArray(6)
    val ambientSky = floatArrayOf(0f, 0f, 0f)
    val ambientGround = floatArrayOf(0f, 0f, 0f)
    var fogOn = 0f
    var fogNear = 10f
    var fogFar = 60f
    val fogColor = floatArrayOf(0.5f, 0.5f, 0.5f)

    class Item(val shape: String, val model: FloatArray, val color: FloatArray, val rotM: FloatArray, val opacity: Float)

    companion object {

        fun load(context: Context): Scene {
            val scene = Scene()
            val text = context.assets.open("scene.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val bgArr = vec(root.optJSONArray("bg"), scene.bg)
            scene.bg[0] = bgArr[0]; scene.bg[1] = bgArr[1]; scene.bg[2] = bgArr[2]
            val start = root.optJSONObject("start")
            if (start != null) {
                val p = vec(start.optJSONArray("pos"), floatArrayOf(0f, 0f, 0f))
                scene.startX = p[0]
                scene.startZ = p[2]
                scene.startYaw = start.optDouble("yaw", 0.0).toFloat()
            }
            scene.eyeHeight = root.optDouble("eyeHeight", 1.6).toFloat()
            readSky(root, scene)
            readLights(root, scene)
            readFog(root, scene)
            val list = root.optJSONArray("objects") ?: return scene
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val shape = o.optString("shape", "")
                if (shape.isEmpty()) continue
                val p = vec(o.optJSONArray("pos"), floatArrayOf(0f, 0f, 0f))
                val rot = vec(o.optJSONArray("rot"), floatArrayOf(0f, 0f, 0f))
                val scl = vec(o.optJSONArray("scale"), floatArrayOf(1f, 1f, 1f))
                val col = vec(o.optJSONArray("color"), floatArrayOf(0.5f, 0.5f, 0.5f))
                val rotation = FloatArray(16)
                Matrix.setIdentityM(rotation, 0)
                Matrix.rotateM(rotation, 0, rot[0] * 57.29578f, 1f, 0f, 0f)
                Matrix.rotateM(rotation, 0, rot[1] * 57.29578f, 0f, 1f, 0f)
                Matrix.rotateM(rotation, 0, rot[2] * 57.29578f, 0f, 0f, 1f)
                val placed = FloatArray(16)
                Matrix.setIdentityM(placed, 0)
                Matrix.translateM(placed, 0, p[0], p[1], p[2])
                val model = FloatArray(16)
                Matrix.multiplyMM(model, 0, placed, 0, rotation, 0)
                Matrix.scaleM(model, 0, scl[0], scl[1], scl[2])
                // The item's own rotation goes to the shader, which brings the scene's
                // lights into the item's frame — so a turned object is lit on the side
                // that faces the sun, exactly as the editor lights it.
                val rotM = floatArrayOf(
                    rotation[0], rotation[1], rotation[2],
                    rotation[4], rotation[5], rotation[6],
                    rotation[8], rotation[9], rotation[10])
                scene.items.add(Item(shape, model, col, rotM, o.optDouble("opacity", 1.0).toFloat()))
            }
            return scene
        }

        private fun copy3(a: JSONArray?, out: FloatArray, at: Int = 0) {
            if (a == null || a.length() < 3) return
            out[at] = a.optDouble(0, 0.0).toFloat()
            out[at + 1] = a.optDouble(1, 0.0).toFloat()
            out[at + 2] = a.optDouble(2, 0.0).toFloat()
        }

        private fun readSky(root: JSONObject, scene: Scene) {
            val sky = root.optJSONObject("sky") ?: return
            scene.skyType = if (sky.optString("type", "flat") == "procedural") "procedural" else "flat"
            copy3(sky.optJSONArray("top"), scene.skyTop)
            copy3(sky.optJSONArray("horizon"), scene.skyHorizon)
            copy3(sky.optJSONArray("sunDir"), scene.skySunDir)
            copy3(sky.optJSONArray("sunColor"), scene.skySunColor)
            copy3(sky.optJSONArray("color"), scene.flatSky)
            scene.skySunSize = sky.optDouble("sunSize", 0.02).toFloat()
            scene.skyStars = sky.optDouble("starDensity", 0.0).toFloat()
            scene.skyHaze = sky.optDouble("hazeStrength", 0.25).toFloat()
            scene.skyGlow = sky.optDouble("sunGlow", 1.0).toFloat()
        }

        private fun readLights(root: JSONObject, scene: Scene) {
            val lights = root.optJSONObject("lights") ?: return
            val suns = lights.optJSONArray("suns")
            if (suns != null) {
                val count = minOf(2, suns.length())
                for (i in 0 until count) {
                    val sun = suns.optJSONObject(i) ?: continue
                    copy3(sun.optJSONArray("dir"), scene.sunDir, i * 3)
                    copy3(sun.optJSONArray("color"), scene.sunColor, i * 3)
                }
            }
            copy3(lights.optJSONArray("ambientSky"), scene.ambientSky)
            copy3(lights.optJSONArray("ambientGround"), scene.ambientGround)
        }

        private fun readFog(root: JSONObject, scene: Scene) {
            val fog = root.optJSONObject("fog") ?: return
            scene.fogOn = 1f
            scene.fogNear = fog.optDouble("near", 10.0).toFloat()
            scene.fogFar = fog.optDouble("far", 60.0).toFloat()
            copy3(fog.optJSONArray("color"), scene.fogColor)
        }

        private fun vec(a: JSONArray?, fallback: FloatArray): FloatArray {
            if (a == null || a.length() < 3) return floatArrayOf(fallback[0], fallback[1], fallback[2])
            return floatArrayOf(a.optDouble(0, 0.0).toFloat(), a.optDouble(1, 0.0).toFloat(), a.optDouble(2, 0.0).toFloat())
        }
    }
}
