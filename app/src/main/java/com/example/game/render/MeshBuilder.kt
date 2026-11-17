package com.example.game.render

import com.example.game.world.DoorSide
import com.example.game.world.HouseSpec
import com.example.game.world.PropKind
import com.example.game.world.TownWorld
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Packed vertex format: [px, py, pz, nx, ny, nz, r, g, b, u, v] = 11 floats per vertex.
 */
class MeshBuilder(initialFloats: Int = 65536) {
  private var data = FloatArray(initialFloats)
  private var size = 0

  val vertexCount: Int
    get() = size / FLOATS_PER_VERTEX

  fun toFloatBuffer(): FloatBuffer {
    val bb = ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder())
    val fb = bb.asFloatBuffer()
    fb.put(data, 0, size)
    fb.position(0)
    return fb
  }

  private fun ensureCapacity(extraFloats: Int) {
    if (size + extraFloats > data.size) {
      data = data.copyOf(maxOf(data.size * 2, size + extraFloats))
    }
  }

  fun addVertex(
    x: Float,
    y: Float,
    z: Float,
    nx: Float,
    ny: Float,
    nz: Float,
    r: Float,
    g: Float,
    b: Float,
    u: Float = 0f,
    v: Float = 0f,
  ) {
    ensureCapacity(FLOATS_PER_VERTEX)
    data[size++] = x
    data[size++] = y
    data[size++] = z
    data[size++] = nx
    data[size++] = ny
    data[size++] = nz
    data[size++] = r
    data[size++] = g
    data[size++] = b
    data[size++] = u
    data[size++] = v
  }

  fun addTri(
    ax: Float, ay: Float, az: Float,
    bx: Float, by: Float, bz: Float,
    cx: Float, cy: Float, cz: Float,
    r: Float, g: Float, b: Float,
    au: Float = 0f, av: Float = 0f,
    bu: Float = 0f, bv: Float = 0f,
    cu: Float = 0f, cv: Float = 0f,
  ) {
    val ux = bx - ax
    val uy = by - ay
    val uz = bz - az
    val vx = cx - ax
    val vy = cy - ay
    val vz = cz - az
    var nx = uy * vz - uz * vy
    var ny = uz * vx - ux * vz
    var nz = ux * vy - uy * vx
    val len = sqrt(nx * nx + ny * ny + nz * nz)
    if (len > 1e-6f) {
      nx /= len
      ny /= len
      nz /= len
    } else {
      ny = 1f
    }
    addVertex(ax, ay, az, nx, ny, nz, r, g, b, au, av)
    addVertex(bx, by, bz, nx, ny, nz, r, g, b, bu, bv)
    addVertex(cx, cy, cz, nx, ny, nz, r, g, b, cu, cv)
  }

  fun addQuad(
    ax: Float, ay: Float, az: Float,
    bx: Float, by: Float, bz: Float,
    cx: Float, cy: Float, cz: Float,
    dx: Float, dy: Float, dz: Float,
    r: Float, g: Float, b: Float,
    uvMode: Boolean = false,
  ) {
    if (uvMode) {
      addTri(ax, ay, az, bx, by, bz, cx, cy, cz, r, g, b, 0f, 1f, 1f, 1f, 1f, 0f)
      addTri(ax, ay, az, cx, cy, cz, dx, dy, dz, r, g, b, 0f, 1f, 1f, 0f, 0f, 0f)
    } else {
      addTri(ax, ay, az, bx, by, bz, cx, cy, cz, r, g, b)
      addTri(ax, ay, az, cx, cy, cz, dx, dy, dz, r, g, b)
    }
  }

  fun addBox(
    minX: Float, minY: Float, minZ: Float,
    maxX: Float, maxY: Float, maxZ: Float,
    r: Float, g: Float, b: Float,
  ) {
    // Front (+Z)
    addQuad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b)
    // Back (-Z)
    addQuad(maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, r, g, b)
    // Left (-X)
    addQuad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b)
    // Right (+X)
    addQuad(maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b)
    // Top (+Y)
    addQuad(minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, r, g, b)
    // Bottom (-Y)
    addQuad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b)
  }

  fun addRotatedBox(
    cx: Float, cy: Float, cz: Float,
    hx: Float, hy: Float, hz: Float,
    yawDeg: Float,
    r: Float, g: Float, b: Float,
  ) {
    val rad = yawDeg * (PI.toFloat() / 180f)
    val c = cos(rad)
    val s = sin(rad)
    fun tx(lx: Float, lz: Float) = cx + lx * c - lz * s
    fun tz(lx: Float, lz: Float) = cz + lx * s + lz * c

    val y0 = cy - hy
    val y1 = cy + hy
    val p0x = tx(-hx, -hz); val p0z = tz(-hx, -hz)
    val p1x = tx(hx, -hz); val p1z = tz(hx, -hz)
    val p2x = tx(hx, hz); val p2z = tz(hx, hz)
    val p3x = tx(-hx, hz); val p3z = tz(-hx, hz)

    addQuad(p3x, y0, p3z, p2x, y0, p2z, p2x, y1, p2z, p3x, y1, p3z, r, g, b)
    addQuad(p1x, y0, p1z, p0x, y0, p0z, p0x, y1, p0z, p1x, y1, p1z, r, g, b)
    addQuad(p0x, y0, p0z, p3x, y0, p3z, p3x, y1, p3z, p0x, y1, p0z, r, g, b)
    addQuad(p2x, y0, p2z, p1x, y0, p1z, p1x, y1, p1z, p2x, y1, p2z, r, g, b)
    addQuad(p3x, y1, p3z, p2x, y1, p2z, p1x, y1, p1z, p0x, y1, p0z, r, g, b)
    addQuad(p0x, y0, p0z, p1x, y0, p1z, p2x, y0, p2z, p3x, y0, p3z, r, g, b)
  }

  fun addCylinder(
    cx: Float, baseY: Float, cz: Float,
    radiusBot: Float, radiusTop: Float, height: Float,
    segments: Int,
    r: Float, g: Float, b: Float,
    tiltX: Float = 0f,
  ) {
    val topY = baseY + height
    for (i in 0 until segments) {
      val a0 = (i.toFloat() / segments) * (2f * PI.toFloat())
      val a1 = ((i + 1).toFloat() / segments) * (2f * PI.toFloat())
      val bx0 = cx + cos(a0) * radiusBot
      val bz0 = cz + sin(a0) * radiusBot
      val bx1 = cx + cos(a1) * radiusBot
      val bz1 = cz + sin(a1) * radiusBot
      val tx0 = cx + tiltX + cos(a0) * radiusTop
      val tz0 = cz + sin(a0) * radiusTop
      val tx1 = cx + tiltX + cos(a1) * radiusTop
      val tz1 = cz + sin(a1) * radiusTop
      addQuad(bx0, baseY, bz0, bx1, baseY, bz1, tx1, topY, tz1, tx0, topY, tz0, r, g, b)
      addTri(cx + tiltX, topY, cz, tx0, topY, tz0, tx1, topY, tz1, r, g, b)
    }
  }

  companion object {
    const val FLOATS_PER_VERTEX = 11
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4
  }
}

object WorldMeshFactory {

  fun buildTownMesh(world: TownWorld): MeshBuilder {
    val mb = MeshBuilder(160000)

    // 1. Mediterranean dark water plane & coastal sand strip & cobblestone plaza
    mb.addQuad(-95f, -0.35f, -31f, 95f, -0.35f, -31f, 95f, -0.35f, -105f, -95f, -0.35f, -105f, 0.08f, 0.16f, 0.28f)
    mb.addQuad(-55f, 0f, 44f, 55f, 0f, 44f, 55f, 0f, -31f, -55f, 0f, -31f, 0.22f, 0.21f, 0.28f)
    // Main North-South avenue & central plaza tiles
    mb.addQuad(-7f, 0.02f, 42f, 7f, 0.02f, 42f, 7f, 0.02f, -30f, -7f, 0.02f, -30f, 0.28f, 0.26f, 0.34f)
    mb.addQuad(-14f, 0.025f, 12f, 14f, 0.025f, 12f, 14f, 0.025f, -15f, -14f, 0.025f, -15f, 0.30f, 0.28f, 0.35f)
    // Seafront promenade stone curb
    mb.addBox(-45f, 0f, -31.2f, 45f, 0.22f, -29.8f, 0.42f, 0.39f, 0.44f)
    // Wooden harbor pier & moored low-poly fishing boat
    mb.addBox(-3.4f, 0.05f, -45.2f, 3.4f, 0.32f, -30f, 0.42f, 0.27f, 0.17f)
    // Pier pilings
    for (pz in listOf(-33f, -37f, -41f, -44.8f)) {
      mb.addBox(-3.3f, -0.5f, pz - 0.2f, -2.9f, 1.15f, pz + 0.2f, 0.30f, 0.19f, 0.12f)
      mb.addBox(2.9f, -0.5f, pz - 0.2f, 3.3f, 1.15f, pz + 0.2f, 0.30f, 0.19f, 0.12f)
    }
    // Moored low-poly fishing boat beside pier
    mb.addBox(5.2f, -0.25f, -43f, 8.2f, 0.85f, -36f, 0.72f, 0.36f, 0.26f)
    mb.addBox(5.8f, 0.85f, -40.5f, 7.6f, 2.0f, -38f, 0.88f, 0.84f, 0.78f)

    // Seafront balustrade & town perimeter walls
    mb.addBox(-45f, 0.2f, -30.5f, -2.4f, 1.15f, -30.0f, 0.56f, 0.52f, 0.54f)
    mb.addBox(2.4f, 0.2f, -30.5f, 45f, 1.15f, -30.0f, 0.56f, 0.52f, 0.54f)
    mb.addBox(-45.5f, 0f, -31f, -44.2f, 3.0f, 41f, 0.46f, 0.43f, 0.48f)
    mb.addBox(44.2f, 0f, -31f, 45.5f, 3.0f, 41f, 0.46f, 0.43f, 0.48f)
    mb.addBox(-45.5f, 0f, 40.2f, 45.5f, 3.0f, 41.5f, 0.46f, 0.43f, 0.48f)

    // 2. Enterable Spanish plaster houses with red terracotta gabled roofs & warm glowing windows
    for (h in world.houses) {
      buildHouseGeometry(mb, h)
    }

    // 3. Street lamps with glowing amber heads
    for (lamp in world.lamps) {
      mb.addCylinder(lamp.x, 0f, lamp.z, 0.14f, 0.10f, 3.8f, 6, 0.18f, 0.19f, 0.24f)
      // Glowing warm amber lantern bulb (r > 0.96 triggers emissive glow in shader)
      mb.addBox(lamp.x - 0.28f, 3.75f, lamp.z - 0.28f, lamp.x + 0.28f, 4.30f, lamp.z + 0.28f, 1.0f, 0.82f, 0.40f)
      mb.addBox(lamp.x - 0.34f, 4.30f, lamp.z - 0.34f, lamp.x + 0.34f, 4.46f, lamp.z + 0.34f, 0.15f, 0.15f, 0.20f)
    }

    // 4. Town & interior props
    for (prop in world.props) {
      when (prop.kind) {
        PropKind.CAR -> buildCar(mb, prop.x, prop.z, prop.yawDeg, prop.colorIndex)
        PropKind.PALM -> buildPalmTree(mb, prop.x, prop.z)
        PropKind.BARREL -> {
          val isRed = prop.colorIndex % 2 == 0
          mb.addCylinder(
            prop.x, 0f, prop.z, 0.40f, 0.40f, 1.08f, 8,
            if (isRed) 0.76f else 0.35f,
            if (isRed) 0.22f else 0.44f,
            if (isRed) 0.20f else 0.50f,
          )
        }
        PropKind.CRATE -> {
          mb.addBox(prop.x - 0.55f, 0f, prop.z - 0.55f, prop.x + 0.55f, 1.1f, prop.z + 0.55f, 0.55f, 0.38f, 0.22f)
          mb.addBox(prop.x - 0.57f, 0.48f, prop.z - 0.57f, prop.x + 0.57f, 0.62f, prop.z + 0.57f, 0.38f, 0.25f, 0.14f)
        }
        PropKind.COUNTER -> {
          mb.addBox(prop.x - 1.0f, 0f, prop.z - 0.45f, prop.x + 1.0f, 1.05f, prop.z + 0.45f, 0.46f, 0.31f, 0.20f)
        }
        PropKind.TABLE -> {
          mb.addBox(prop.x - 0.8f, 0.72f, prop.z - 0.6f, prop.x + 0.8f, 0.84f, prop.z + 0.6f, 0.50f, 0.34f, 0.21f)
          mb.addBox(prop.x - 0.7f, 0f, prop.z - 0.5f, prop.x - 0.55f, 0.72f, prop.z - 0.35f, 0.34f, 0.22f, 0.14f)
          mb.addBox(prop.x + 0.55f, 0f, prop.z - 0.5f, prop.x + 0.7f, 0.72f, prop.z - 0.35f, 0.34f, 0.22f, 0.14f)
          mb.addBox(prop.x - 0.7f, 0f, prop.z + 0.35f, prop.x - 0.55f, 0.72f, prop.z + 0.5f, 0.34f, 0.22f, 0.14f)
          mb.addBox(prop.x + 0.55f, 0f, prop.z + 0.35f, prop.x + 0.7f, 0.72f, prop.z + 0.5f, 0.34f, 0.22f, 0.14f)
        }
      }
    }

    return mb
  }

  private fun buildHouseGeometry(mb: MeshBuilder, h: HouseSpec) {
    val t = 0.45f
    val wallH = 3.6f
    val midX = (h.minX + h.maxX) * 0.5f
    val midZ = (h.minZ + h.maxZ) * 0.5f
    val doorHalf = 1.25f
    val doorTop = 2.45f
    val wr = h.wallColor[0]
    val wg = h.wallColor[1]
    val wb = h.wallColor[2]

    // Interior terracotta floor
    mb.addQuad(h.minX, 0.04f, h.maxZ, h.maxX, 0.04f, h.maxZ, h.maxX, 0.04f, h.minZ, h.minX, 0.04f, h.minZ, 0.58f, 0.34f, 0.24f)

    // North wall
    if (h.doorSide == DoorSide.NORTH) {
      mb.addBox(h.minX, 0f, h.minZ, midX - doorHalf, wallH, h.minZ + t, wr, wg, wb)
      mb.addBox(midX + doorHalf, 0f, h.minZ, h.maxX, wallH, h.minZ + t, wr, wg, wb)
      mb.addBox(midX - doorHalf, doorTop, h.minZ, midX + doorHalf, wallH, h.minZ + t, wr, wg, wb)
    } else {
      mb.addBox(h.minX, 0f, h.minZ, h.maxX, wallH, h.minZ + t, wr, wg, wb)
      addWarmWindow(mb, midX, 1.85f, h.minZ - 0.03f, 1.6f, 1.1f, isZAxis = true)
    }

    // South wall
    if (h.doorSide == DoorSide.SOUTH) {
      mb.addBox(h.minX, 0f, h.maxZ - t, midX - doorHalf, wallH, h.maxZ, wr, wg, wb)
      mb.addBox(midX + doorHalf, 0f, h.maxZ - t, h.maxX, wallH, h.maxZ, wr, wg, wb)
      mb.addBox(midX - doorHalf, doorTop, h.maxZ - t, midX + doorHalf, wallH, h.maxZ, wr, wg, wb)
    } else {
      mb.addBox(h.minX, 0f, h.maxZ - t, h.maxX, wallH, h.maxZ, wr, wg, wb)
      addWarmWindow(mb, midX, 1.85f, h.maxZ + 0.03f, 1.6f, 1.1f, isZAxis = true)
    }

    // West wall
    if (h.doorSide == DoorSide.WEST) {
      mb.addBox(h.minX, 0f, h.minZ, h.minX + t, wallH, midZ - doorHalf, wr, wg, wb)
      mb.addBox(h.minX, 0f, midZ + doorHalf, h.minX + t, wallH, h.maxZ, wr, wg, wb)
      mb.addBox(h.minX, doorTop, midZ - doorHalf, h.minX + t, wallH, midZ + doorHalf, wr, wg, wb)
      // Shop awning over door
      mb.addBox(h.minX - 0.9f, 2.55f, midZ - 2.6f, h.minX + 0.05f, 2.75f, midZ + 2.6f, 0.85f, 0.42f, 0.18f)
    } else {
      mb.addBox(h.minX, 0f, h.minZ, h.minX + t, wallH, h.maxZ, wr, wg, wb)
      addWarmWindow(mb, h.minX - 0.03f, 1.85f, midZ, 1.6f, 1.1f, isZAxis = false)
    }

    // East wall
    if (h.doorSide == DoorSide.EAST) {
      mb.addBox(h.maxX - t, 0f, h.minZ, h.maxX, wallH, midZ - doorHalf, wr, wg, wb)
      mb.addBox(h.maxX - t, 0f, midZ + doorHalf, h.maxX, wallH, h.maxZ, wr, wg, wb)
      mb.addBox(h.maxX - t, doorTop, midZ - doorHalf, h.maxX, wallH, midZ + doorHalf, wr, wg, wb)
      mb.addBox(h.maxX - 0.05f, 2.55f, midZ - 2.6f, h.maxX + 0.9f, 2.75f, midZ + 2.6f, 0.24f, 0.54f, 0.56f)
    } else {
      mb.addBox(h.maxX - t, 0f, h.minZ, h.maxX, wallH, h.maxZ, wr, wg, wb)
      addWarmWindow(mb, h.maxX + 0.03f, 1.85f, midZ, 1.6f, 1.1f, isZAxis = false)
    }

    // Pitched red terracotta tile roof
    val rr = h.roofColor[0]
    val rg = h.roofColor[1]
    val rb = h.roofColor[2]
    val overhang = 0.55f
    val rx0 = h.minX - overhang
    val rx1 = h.maxX + overhang
    val rz0 = h.minZ - overhang
    val rz1 = h.maxZ + overhang
    val peakY = wallH + 1.85f

    // Ceiling underside
    mb.addQuad(h.minX, wallH - 0.02f, h.minZ, h.maxX, wallH - 0.02f, h.minZ, h.maxX, wallH - 0.02f, h.maxZ, h.minX, wallH - 0.02f, h.maxZ, 0.36f, 0.25f, 0.18f)
    // Sloped roof faces
    mb.addQuad(rx0, wallH, rz1, rx1, wallH, rz1, rx1, peakY, midZ, rx0, peakY, midZ, rr, rg, rb)
    mb.addQuad(rx1, wallH, rz0, rx0, wallH, rz0, rx0, peakY, midZ, rx1, peakY, midZ, rr, rg, rb)
    // Gables
    mb.addTri(rx0, wallH, rz1, rx0, peakY, midZ, rx0, wallH, rz0, wr * 0.92f, wg * 0.92f, wb * 0.92f)
    mb.addTri(rx1, wallH, rz0, rx1, peakY, midZ, rx1, wallH, rz1, wr * 0.92f, wg * 0.92f, wb * 0.92f)
  }

  private fun addWarmWindow(
    mb: MeshBuilder,
    x: Float,
    y: Float,
    z: Float,
    w: Float,
    h: Float,
    isZAxis: Boolean,
  ) {
    val hw = w * 0.5f
    val hh = h * 0.5f
    if (isZAxis) {
      mb.addBox(x - hw - 0.1f, y - hh - 0.1f, z - 0.04f, x + hw + 0.1f, y + hh + 0.1f, z + 0.04f, 0.22f, 0.16f, 0.13f)
      mb.addBox(x - hw, y - hh, z - 0.06f, x + hw, y + hh, z + 0.06f, 1.0f, 0.78f, 0.36f)
    } else {
      mb.addBox(x - 0.04f, y - hh - 0.1f, z - hw - 0.1f, x + 0.04f, y + hh + 0.1f, z + hw + 0.1f, 0.22f, 0.16f, 0.13f)
      mb.addBox(x - 0.06f, y - hh, z - hw, x + 0.06f, y + hh, z + hw, 1.0f, 0.78f, 0.36f)
    }
  }

  private fun buildCar(mb: MeshBuilder, cx: Float, cz: Float, yawDeg: Float, colorIdx: Int) {
    val bodyColors =
      arrayOf(
        floatArrayOf(0.75f, 0.24f, 0.22f),
        floatArrayOf(0.24f, 0.45f, 0.65f),
        floatArrayOf(0.78f, 0.74f, 0.66f),
        floatArrayOf(0.26f, 0.52f, 0.40f),
        floatArrayOf(0.58f, 0.44f, 0.22f),
      )
    val col = bodyColors[colorIdx % bodyColors.size]
    mb.addRotatedBox(cx, 0.52f, cz, 0.98f, 0.38f, 1.95f, yawDeg, col[0], col[1], col[2])
    mb.addRotatedBox(cx, 1.12f, cz, 0.84f, 0.32f, 1.05f, yawDeg, col[0] * 0.9f, col[1] * 0.9f, col[2] * 0.9f)
    mb.addRotatedBox(cx, 1.14f, cz, 0.86f, 0.22f, 0.92f, yawDeg, 0.18f, 0.24f, 0.34f)
    // Wheels
    for (wx in listOf(-0.92f, 0.92f)) {
      for (wz in listOf(-1.25f, 1.25f)) {
        val rad = yawDeg * (PI.toFloat() / 180f)
        val px = cx + wx * cos(rad) - wz * sin(rad)
        val pz = cz + wx * sin(rad) + wz * cos(rad)
        mb.addRotatedBox(px, 0.28f, pz, 0.18f, 0.28f, 0.32f, yawDeg, 0.10f, 0.10f, 0.12f)
      }
    }
  }

  private fun buildPalmTree(mb: MeshBuilder, x: Float, z: Float) {
    // Segmented curved trunk
    mb.addCylinder(x, 0f, z, 0.28f, 0.22f, 1.8f, 6, 0.46f, 0.32f, 0.22f, 0.18f)
    mb.addCylinder(x + 0.18f, 1.8f, z, 0.22f, 0.16f, 1.8f, 6, 0.49f, 0.34f, 0.23f, 0.24f)
    mb.addCylinder(x + 0.42f, 3.6f, z, 0.16f, 0.11f, 1.7f, 6, 0.52f, 0.36f, 0.24f, 0.16f)

    val topX = x + 0.58f
    val topY = 5.3f
    val topZ = z
    // 6 low-poly radiating fronds
    for (i in 0 until 6) {
      val a = (i.toFloat() / 6f) * (2f * PI.toFloat())
      val dx = cos(a)
      val dz = sin(a)
      val midX = topX + dx * 1.4f
      val midY = topY + 0.45f
      val midZ = topZ + dz * 1.4f
      val tipX = topX + dx * 2.7f
      val tipY = topY - 0.55f
      val tipZ = topZ + dz * 2.7f
      val px = -dz * 0.42f
      val pz = dx * 0.42f
      mb.addQuad(
        topX - px * 0.4f, topY, topZ - pz * 0.4f,
        topX + px * 0.4f, topY, topZ + pz * 0.4f,
        midX + px, midY, midZ + pz,
        midX - px, midY, midZ - pz,
        0.18f, 0.48f, 0.24f,
      )
      mb.addTri(
        midX - px, midY, midZ - pz,
        midX + px, midY, midZ + pz,
        tipX, tipY, tipZ,
        0.14f, 0.42f, 0.20f,
      )
    }
  }

  /** Builds a dynamic articulated unit mesh: Zombie or Raider or Loot or Weapon Viewmodel. */
  fun buildZombieMesh(isRunner: Boolean, isArmed: Boolean): MeshBuilder {
    val mb = MeshBuilder(2048)
    val skin = if (isRunner) floatArrayOf(0.48f, 0.62f, 0.44f) else floatArrayOf(0.42f, 0.58f, 0.46f)
    val shirt =
      when {
        isArmed -> floatArrayOf(0.22f, 0.24f, 0.28f)
        isRunner -> floatArrayOf(0.65f, 0.22f, 0.22f)
        else -> floatArrayOf(0.26f, 0.34f, 0.44f)
      }
    val pants = floatArrayOf(0.18f, 0.19f, 0.26f)
    // Legs
    mb.addBox(-0.22f, 0f, -0.11f, -0.04f, 0.78f, 0.11f, pants[0], pants[1], pants[2])
    mb.addBox(0.04f, 0f, -0.11f, 0.22f, 0.78f, 0.11f, pants[0], pants[1], pants[2])
    // Torso
    mb.addBox(-0.26f, 0.78f, -0.15f, 0.26f, 1.44f, 0.15f, shirt[0], shirt[1], shirt[2])
    if (isArmed) {
      // Tactical vest + shoulder pads
      mb.addBox(-0.29f, 0.92f, -0.18f, 0.29f, 1.42f, 0.18f, 0.14f, 0.18f, 0.16f)
    }
    // Outstretched arms (+Z forward)
    mb.addBox(-0.38f, 1.15f, 0.02f, -0.24f, 1.38f, 0.66f, skin[0], skin[1], skin[2])
    mb.addBox(0.24f, 1.15f, 0.02f, 0.38f, 1.38f, 0.66f, skin[0], skin[1], skin[2])
    // Head
    mb.addBox(-0.18f, 1.46f, -0.17f, 0.18f, 1.84f, 0.17f, skin[0], skin[1], skin[2])
    // Glowing eerie zombie eyes (+Z)
    val eyeR = if (isRunner) 1.0f else 0.98f
    val eyeG = if (isRunner) 0.28f else 0.85f
    val eyeB = if (isRunner) 0.22f else 0.24f
    mb.addBox(-0.11f, 1.62f, 0.16f, -0.04f, 1.68f, 0.19f, eyeR, eyeG, eyeB)
    mb.addBox(0.04f, 1.62f, 0.16f, 0.11f, 1.68f, 0.19f, eyeR, eyeG, eyeB)
    return mb
  }

  fun buildPickupBoxMesh(r: Float, g: Float, b: Float, tall: Boolean = false): MeshBuilder {
    val mb = MeshBuilder(512)
    val h = if (tall) 0.72f else 0.42f
    val w = if (tall) 0.42f else 0.28f
    mb.addBox(-w, 0.05f, -w, w, h, w, r, g, b)
    mb.addBox(-w * 1.05f, h * 0.42f, -w * 1.05f, w * 1.05f, h * 0.56f, w * 1.05f, 0.98f, 0.94f, 0.75f)
    return mb
  }

  fun buildUnitBoxMesh(r: Float, g: Float, b: Float): MeshBuilder {
    val mb = MeshBuilder(256)
    mb.addBox(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, r, g, b)
    return mb
  }

  fun buildM4ViewModel(): MeshBuilder {
    val mb = MeshBuilder(1024)
    // Upper + lower receiver
    mb.addBox(-0.045f, -0.05f, -0.22f, 0.045f, 0.045f, 0.10f, 0.18f, 0.19f, 0.22f)
    // Handguard & barrel
    mb.addBox(-0.036f, -0.03f, -0.52f, 0.036f, 0.036f, -0.22f, 0.24f, 0.25f, 0.29f)
    mb.addCylinder(0f, 0.005f, -0.74f, 0.015f, 0.015f, 0.24f, 6, 0.14f, 0.15f, 0.18f)
    // Curved magazine
    mb.addBox(-0.025f, -0.20f, -0.14f, 0.025f, -0.04f, -0.05f, 0.14f, 0.15f, 0.17f)
    // Pistol grip & buttstock
    mb.addBox(-0.024f, -0.16f, 0.03f, 0.024f, -0.04f, 0.10f, 0.15f, 0.15f, 0.18f)
    mb.addBox(-0.032f, -0.06f, 0.10f, 0.032f, 0.035f, 0.28f, 0.20f, 0.21f, 0.24f)
    // Holo/red-dot sight housing on top rail
    mb.addBox(-0.032f, 0.045f, -0.16f, -0.020f, 0.105f, -0.03f, 0.14f, 0.14f, 0.16f)
    mb.addBox(0.020f, 0.045f, -0.16f, 0.032f, 0.105f, -0.03f, 0.14f, 0.14f, 0.16f)
    mb.addBox(-0.032f, 0.095f, -0.16f, 0.032f, 0.110f, -0.03f, 0.14f, 0.14f, 0.16f)
    // Glowing red dot inside holo window
    mb.addBox(-0.004f, 0.068f, -0.09f, 0.004f, 0.076f, -0.08f, 1.0f, 0.22f, 0.22f)
    return mb
  }

  fun buildGlockViewModel(): MeshBuilder {
    val mb = MeshBuilder(512)
    // Slide
    mb.addBox(-0.028f, 0.01f, -0.22f, 0.028f, 0.06f, 0.04f, 0.20f, 0.22f, 0.26f)
    // Polymer frame
    mb.addBox(-0.025f, -0.02f, -0.20f, 0.025f, 0.01f, 0.03f, 0.13f, 0.13f, 0.15f)
    // Grip
    mb.addBox(-0.024f, -0.15f, -0.03f, 0.024f, -0.01f, 0.035f, 0.14f, 0.14f, 0.16f)
    // Tritium front sight dot
    mb.addBox(-0.004f, 0.06f, -0.21f, 0.004f, 0.07f, -0.19f, 0.95f, 0.92f, 0.35f)
    return mb
  }

  fun buildBatViewModel(): MeshBuilder {
    val mb = MeshBuilder(512)
    // Grip tape
    mb.addCylinder(0f, -0.16f, 0f, 0.024f, 0.026f, 0.22f, 8, 0.15f, 0.15f, 0.18f)
    // Ashwood barrel
    mb.addCylinder(0f, 0.06f, 0f, 0.026f, 0.048f, 0.56f, 8, 0.72f, 0.54f, 0.32f)
    return mb
  }

  fun buildKnifeViewModel(): MeshBuilder {
    val mb = MeshBuilder(256)
    mb.addBox(-0.018f, -0.12f, -0.02f, 0.018f, 0.01f, 0.02f, 0.16f, 0.15f, 0.14f)
    mb.addBox(-0.032f, 0.01f, -0.026f, 0.032f, 0.028f, 0.026f, 0.25f, 0.26f, 0.30f)
    mb.addBox(-0.008f, 0.028f, -0.018f, 0.008f, 0.24f, 0.014f, 0.78f, 0.82f, 0.88f)
    return mb
  }

  fun buildSignQuad(x: Float, y: Float, z: Float, w: Float, h: Float, facingEast: Boolean): MeshBuilder {
    val mb = MeshBuilder(64)
    val hw = w * 0.5f
    val hh = h * 0.5f
    if (facingEast) {
      mb.addQuad(
        x, y - hh, z + hw,
        x, y - hh, z - hw,
        x, y + hh, z - hw,
        x, y + hh, z + hw,
        1f, 1f, 1f, uvMode = true,
      )
    } else {
      mb.addQuad(
        x, y - hh, z - hw,
        x, y - hh, z + hw,
        x, y + hh, z + hw,
        x, y + hh, z - hw,
        1f, 1f, 1f, uvMode = true,
      )
    }
    return mb
  }
}
