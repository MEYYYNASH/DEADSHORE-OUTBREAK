package com.example.game.world

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Axis-aligned 3D/2D collider for buildings, interior furniture, parked cars, barrels, crates, fences, and palms.
 */
data class BoxCollider(
  val minX: Float,
  val maxX: Float,
  val minZ: Float,
  val maxZ: Float,
  val minY: Float = 0f,
  val maxY: Float = 3.2f,
  val blocksSight: Boolean = true,
  val tag: String = "prop",
)

data class HouseSpec(
  val name: String,
  val minX: Float,
  val maxX: Float,
  val minZ: Float,
  val maxZ: Float,
  val doorSide: DoorSide,
  val wallColor: FloatArray,
  val roofColor: FloatArray,
  val signText: String? = null,
)

enum class DoorSide {
  NORTH, // -Z
  SOUTH, // +Z
  EAST,  // +X
  WEST,  // -X
}

data class StreetLampSpec(
  val x: Float,
  val z: Float,
)

data class PropSpec(
  val kind: PropKind,
  val x: Float,
  val z: Float,
  val yawDeg: Float = 0f,
  val colorIndex: Int = 0,
)

enum class PropKind {
  CAR,
  BARREL,
  CRATE,
  PALM,
  TABLE,
  COUNTER,
}

data class LootSpawnPoint(
  val x: Float,
  val z: Float,
  val insideHouse: Boolean,
)

/**
 * Defines the Spanish coastal town ("Costa Oscura") layout, colliders, lighting, and navigation bounds.
 * Coordinate system: +X East, -X West, +Z South (inland), -Z North (harbor & Mediterranean sea).
 */
class TownWorld {
  val colliders = mutableListOf<BoxCollider>()
  val houses = mutableListOf<HouseSpec>()
  val lamps = mutableListOf<StreetLampSpec>()
  val props = mutableListOf<PropSpec>()
  val lootSpawns = mutableListOf<LootSpawnPoint>()
  val spawnPoints = mutableListOf<Pair<Float, Float>>()

  val minBoundX = -44f
  val maxBoundX = 44f
  val minBoundZ = -44f
  val maxBoundZ = 40f

  init {
    buildHouses()
    buildSeawallAndHarbor()
    buildStreetProps()
    buildSpawnAndLootPoints()
  }

  private fun buildHouses() {
    // 1. TIENDA (Corner grocery, West side of central plaza)
    val tienda =
      HouseSpec(
        name = "Tienda",
        minX = -22f,
        maxX = -10f,
        minZ = -10f,
        maxZ = 2f,
        doorSide = DoorSide.EAST,
        wallColor = floatArrayOf(0.86f, 0.82f, 0.75f),
        roofColor = floatArrayOf(0.76f, 0.28f, 0.21f),
        signText = "TIENDA",
      )
    // 2. PANADERIA (Bakery, East side of central plaza)
    val panaderia =
      HouseSpec(
        name = "Panaderia",
        minX = 10f,
        maxX = 22f,
        minZ = -12f,
        maxZ = 0f,
        doorSide = DoorSide.WEST,
        wallColor = floatArrayOf(0.90f, 0.85f, 0.76f),
        roofColor = floatArrayOf(0.80f, 0.30f, 0.22f),
        signText = "PANADERIA",
      )
    // 3. CASA DEL MAR (Harborfront plaster house, North-West)
    val casaMar =
      HouseSpec(
        name = "Casa del Mar",
        minX = -25f,
        maxX = -13f,
        minZ = -26f,
        maxZ = -15f,
        doorSide = DoorSide.SOUTH,
        wallColor = floatArrayOf(0.84f, 0.83f, 0.79f),
        roofColor = floatArrayOf(0.72f, 0.25f, 0.19f),
      )
    // 4. VILLA SOL (South-East residential plaster house)
    val villaSol =
      HouseSpec(
        name = "Villa Sol",
        minX = 11f,
        maxX = 23f,
        minZ = 10f,
        maxZ = 22f,
        doorSide = DoorSide.NORTH,
        wallColor = floatArrayOf(0.88f, 0.81f, 0.71f),
        roofColor = floatArrayOf(0.78f, 0.29f, 0.20f),
      )

    houses.addAll(listOf(tienda, panaderia, casaMar, villaSol))

    // Add perimeter walls with 2.4m doorways for each enterable house
    for (h in houses) {
      val t = 0.45f
      val wallH = 3.6f
      val midX = (h.minX + h.maxX) * 0.5f
      val midZ = (h.minZ + h.maxZ) * 0.5f
      val doorHalf = 1.25f

      // North wall (-Z)
      if (h.doorSide == DoorSide.NORTH) {
        colliders.add(BoxCollider(h.minX, midX - doorHalf, h.minZ, h.minZ + t, 0f, wallH, true, "wall"))
        colliders.add(BoxCollider(midX + doorHalf, h.maxX, h.minZ, h.minZ + t, 0f, wallH, true, "wall"))
      } else {
        colliders.add(BoxCollider(h.minX, h.maxX, h.minZ, h.minZ + t, 0f, wallH, true, "wall"))
      }

      // South wall (+Z)
      if (h.doorSide == DoorSide.SOUTH) {
        colliders.add(BoxCollider(h.minX, midX - doorHalf, h.maxZ - t, h.maxZ, 0f, wallH, true, "wall"))
        colliders.add(BoxCollider(midX + doorHalf, h.maxX, h.maxZ - t, h.maxZ, 0f, wallH, true, "wall"))
      } else {
        colliders.add(BoxCollider(h.minX, h.maxX, h.maxZ - t, h.maxZ, 0f, wallH, true, "wall"))
      }

      // West wall (-X)
      if (h.doorSide == DoorSide.WEST) {
        colliders.add(BoxCollider(h.minX, h.minX + t, h.minZ, midZ - doorHalf, 0f, wallH, true, "wall"))
        colliders.add(BoxCollider(h.minX, h.minX + t, midZ + doorHalf, h.maxZ, 0f, wallH, true, "wall"))
      } else {
        colliders.add(BoxCollider(h.minX, h.minX + t, h.minZ, h.maxZ, 0f, wallH, true, "wall"))
      }

      // East wall (+X)
      if (h.doorSide == DoorSide.EAST) {
        colliders.add(BoxCollider(h.maxX - t, h.maxX, h.minZ, midZ - doorHalf, 0f, wallH, true, "wall"))
        colliders.add(BoxCollider(h.maxX - t, h.maxX, midZ + doorHalf, h.maxZ, 0f, wallH, true, "wall"))
      } else {
        colliders.add(BoxCollider(h.maxX - t, h.maxX, h.minZ, h.maxZ, 0f, wallH, true, "wall"))
      }

      // Interior furniture inside each house
      val fx = h.minX + 2.4f
      val fz = h.minZ + 2.4f
      props.add(PropSpec(PropKind.COUNTER, fx + 0.6f, fz + 0.6f, 0f))
      colliders.add(BoxCollider(fx - 0.5f, fx + 1.6f, fz - 0.2f, fz + 1.1f, 0f, 1.1f, false, "furniture"))

      val tx = h.maxX - 2.8f
      val tz = h.maxZ - 2.6f
      props.add(PropSpec(PropKind.TABLE, tx, tz, 0f))
      colliders.add(BoxCollider(tx - 0.9f, tx + 0.9f, tz - 0.7f, tz + 0.7f, 0f, 0.9f, false, "furniture"))
    }
  }

  private fun buildSeawallAndHarbor() {
    // Seafront stone balustrade along Z = -30, leaving a 4.4m pier entrance at X in [-2.2, 2.2]
    colliders.add(BoxCollider(-45f, -2.4f, -30.6f, -29.8f, 0f, 1.25f, false, "fence"))
    colliders.add(BoxCollider(2.4f, 45f, -30.6f, -29.8f, 0f, 1.25f, false, "fence"))

    // Pier water guardrails so player stays on the wooden harbor deck
    colliders.add(BoxCollider(-3.6f, -3.1f, -45f, -30f, 0f, 1.1f, false, "pier_rail"))
    colliders.add(BoxCollider(3.1f, 3.6f, -45f, -30f, 0f, 1.1f, false, "pier_rail"))
    colliders.add(BoxCollider(-3.6f, 3.6f, -45.5f, -44.8f, 0f, 1.1f, false, "pier_rail"))

    // Outer town perimeter stone walls (East, West, South)
    colliders.add(BoxCollider(-45.5f, -44.2f, -31f, 41f, 0f, 3.2f, true, "boundary"))
    colliders.add(BoxCollider(44.2f, 45.5f, -31f, 41f, 0f, 3.2f, true, "boundary"))
    colliders.add(BoxCollider(-45.5f, 45.5f, 40.2f, 41.5f, 0f, 3.2f, true, "boundary"))
  }

  private fun buildStreetProps() {
    // Vintage street lamps casting warm amber light pools
    val lampCoords =
      listOf(
        -7.5f to -14f,
        7.5f to -14f,
        -7.5f to 4f,
        7.5f to 4f,
        -7.5f to 22f,
        7.5f to 22f,
        -24f to -28f,
        24f to -28f,
        0f to -31f,
        0f to -42f,
      )
    for ((lx, lz) in lampCoords) {
      lamps.add(StreetLampSpec(lx, lz))
      colliders.add(BoxCollider(lx - 0.25f, lx + 0.25f, lz - 0.25f, lz + 0.25f, 0f, 4.2f, false, "lamp"))
    }

    // Parked coastal cars
    val carList =
      listOf(
        Triple(-4.8f, -18f, 0f),
        Triple(5.2f, 14f, 180f),
        Triple(-28f, 8f, 90f),
        Triple(28f, -18f, 270f),
        Triple(28f, 26f, 15f),
      )
    carList.forEachIndexed { idx, (cx, cz, yaw) ->
      props.add(PropSpec(PropKind.CAR, cx, cz, yaw, idx))
      val halfW = if (abs(yaw - 90f) < 30f || abs(yaw - 270f) < 30f) 2.1f else 1.15f
      val halfD = if (abs(yaw - 90f) < 30f || abs(yaw - 270f) < 30f) 1.15f else 2.1f
      colliders.add(BoxCollider(cx - halfW, cx + halfW, cz - halfD, cz + halfD, 0f, 1.55f, true, "car"))
    }

    // Palm trees along promenade and plaza
    val palms =
      listOf(
        -11f to -28f,
        11f to -28f,
        -31f to -27f,
        31f to -27f,
        -6.5f to -4f,
        6.5f to 10f,
        -29f to 22f,
        32f to 4f,
      )
    for ((px, pz) in palms) {
      props.add(PropSpec(PropKind.PALM, px, pz))
      colliders.add(BoxCollider(px - 0.45f, px + 0.45f, pz - 0.45f, pz + 0.45f, 0f, 5.5f, false, "palm"))
    }

    // Barrels & crates along harbor, alleys, and TIENDA
    val barrels =
      listOf(
        -2.0f to -36f,
        1.9f to -39f,
        -9.2f to -8.8f,
        -9.2f to -7.6f,
        9.2f to -1.5f,
        24.5f to 12f,
        -26.5f to -14f,
        -4f to 28f,
      )
    barrels.forEachIndexed { idx, (bx, bz) ->
      props.add(PropSpec(PropKind.BARREL, bx, bz, 0f, idx))
      colliders.add(BoxCollider(bx - 0.45f, bx + 0.45f, bz - 0.45f, bz + 0.45f, 0f, 1.15f, false, "barrel"))
    }

    val crates =
      listOf(
        -1.9f to -41.5f,
        1.8f to -34f,
        -24f to 4.2f,
        24f to -13f,
        4.5f to 25f,
        -14f to 18f,
        15f to -24f,
      )
    crates.forEachIndexed { idx, (cx, cz) ->
      props.add(PropSpec(PropKind.CRATE, cx, cz, 0f, idx))
      colliders.add(BoxCollider(cx - 0.6f, cx + 0.6f, cz - 0.6f, cz + 0.6f, 0f, 1.2f, false, "crate"))
    }
  }

  private fun buildSpawnAndLootPoints() {
    // Loot points both inside enterable houses and in coastal alleys/harbor
    lootSpawns.addAll(
      listOf(
        LootSpawnPoint(-16f, -4f, true),    // Inside TIENDA
        LootSpawnPoint(-18f, -1f, true),    // Inside TIENDA
        LootSpawnPoint(16f, -6f, true),     // Inside PANADERIA
        LootSpawnPoint(14f, -9f, true),     // Inside PANADERIA
        LootSpawnPoint(-19f, -20.5f, true), // Inside Casa del Mar
        LootSpawnPoint(17f, 16f, true),     // Inside Villa Sol
        LootSpawnPoint(0f, -40f, false),    // Harbor pier end
        LootSpawnPoint(-2f, -22f, false),   // Plaza north
        LootSpawnPoint(4f, 8f, false),      // Main avenue
        LootSpawnPoint(-31f, -8f, false),   // West alley
        LootSpawnPoint(31f, -6f, false),    // East alley
        LootSpawnPoint(-12f, 25f, false),   // South street
      )
    )

    // Zombie perimeter spawn points along alleys and seafront
    spawnPoints.addAll(
      listOf(
        -35f to -22f,
        35f to -22f,
        -36f to 5f,
        36f to 6f,
        -34f to 28f,
        34f to 28f,
        0f to 34f,
        -18f to 33f,
        18f to 33f,
        -16f to -28f,
        16f to -28f,
        0f to -38f,
      )
    )
  }

  /** Smooth circle-vs-AABB sliding collision resolution. */
  fun resolveCircleCollision(pos: FloatArray, radius: Float) {
    var x = pos[0].coerceIn(minBoundX, maxBoundX)
    var z = pos[1].coerceIn(minBoundZ, maxBoundZ)

    for (pass in 0 until 2) {
      for (i in colliders.indices) {
        val c = colliders[i]
        val closestX = x.coerceIn(c.minX, c.maxX)
        val closestZ = z.coerceIn(c.minZ, c.maxZ)
        val dx = x - closestX
        val dz = z - closestZ
        val distSq = dx * dx + dz * dz
        if (distSq < radius * radius) {
          if (distSq > 1e-6f) {
            val dist = kotlin.math.sqrt(distSq)
            val push = radius - dist
            x += (dx / dist) * push
            z += (dz / dist) * push
          } else {
            // Inside box: push along shallowest axis
            val leftPen = abs(x - c.minX)
            val rightPen = abs(c.maxX - x)
            val topPen = abs(z - c.minZ)
            val botPen = abs(c.maxZ - z)
            val m = min(min(leftPen, rightPen), min(topPen, botPen))
            when (m) {
              leftPen -> x = c.minX - radius
              rightPen -> x = c.maxX + radius
              topPen -> z = c.minZ - radius
              else -> z = c.maxZ + radius
            }
          }
        }
      }
    }
    pos[0] = x.coerceIn(minBoundX, maxBoundX)
    pos[1] = z.coerceIn(minBoundZ, maxBoundZ)
  }

  /** Fast 3D ray vs world collider slab test for bullet hit occlusion. */
  fun raycastEnvironment(
    ox: Float,
    oy: Float,
    oz: Float,
    dx: Float,
    dy: Float,
    dz: Float,
    maxDist: Float,
  ): Float {
    var closest = maxDist
    // Floor plane y = 0
    if (dy < -1e-4f) {
      val tFloor = -oy / dy
      if (tFloor in 0.01f..closest) closest = tFloor
    }

    for (i in colliders.indices) {
      val c = colliders[i]
      val t = rayVsAabb(ox, oy, oz, dx, dy, dz, c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ)
      if (t in 0.01f..closest) {
        closest = t
      }
    }
    return closest
  }

  /** Checks unobstructed horizontal line-of-sight at chest height (1.3m) for zombie vision. */
  fun hasLineOfSight(x1: Float, z1: Float, x2: Float, z2: Float): Boolean {
    val dx = x2 - x1
    val dz = z2 - z1
    val len = kotlin.math.sqrt(dx * dx + dz * dz)
    if (len < 0.5f) return true
    val nx = dx / len
    val nz = dz / len
    for (i in colliders.indices) {
      val c = colliders[i]
      if (!c.blocksSight) continue
      val t = rayVsAabb(x1, 1.3f, z1, nx, 0f, nz, c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ)
      if (t in 0.01f..(len - 0.3f)) return false
    }
    return true
  }

  private fun rayVsAabb(
    ox: Float,
    oy: Float,
    oz: Float,
    dx: Float,
    dy: Float,
    dz: Float,
    minX: Float,
    minY: Float,
    minZ: Float,
    maxX: Float,
    maxY: Float,
    maxZ: Float,
  ): Float {
    var tMin = 0f
    var tMax = Float.MAX_VALUE

    if (abs(dx) < 1e-6f) {
      if (ox < minX || ox > maxX) return -1f
    } else {
      val inv = 1f / dx
      var t1 = (minX - ox) * inv
      var t2 = (maxX - ox) * inv
      if (t1 > t2) {
        val tmp = t1
        t1 = t2
        t2 = tmp
      }
      tMin = max(tMin, t1)
      tMax = min(tMax, t2)
      if (tMin > tMax) return -1f
    }

    if (abs(dy) < 1e-6f) {
      if (oy < minY || oy > maxY) return -1f
    } else {
      val inv = 1f / dy
      var t1 = (minY - oy) * inv
      var t2 = (maxY - oy) * inv
      if (t1 > t2) {
        val tmp = t1
        t1 = t2
        t2 = tmp
      }
      tMin = max(tMin, t1)
      tMax = min(tMax, t2)
      if (tMin > tMax) return -1f
    }

    if (abs(dz) < 1e-6f) {
      if (oz < minZ || oz > maxZ) return -1f
    } else {
      val inv = 1f / dz
      var t1 = (minZ - oz) * inv
      var t2 = (maxZ - oz) * inv
      if (t1 > t2) {
        val tmp = t1
        t1 = t2
        t2 = tmp
      }
      tMin = max(tMin, t1)
      tMax = min(tMax, t2)
      if (tMin > tMax) return -1f
    }

    return if (tMin > 0f) tMin else -1f
  }
}
