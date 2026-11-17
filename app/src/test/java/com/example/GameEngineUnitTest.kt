package com.example

import com.example.data.EquipmentSlot
import com.example.game.simulation.GameEngine
import com.example.game.world.TownWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineUnitTest {

  @Test
  fun waveTargetFollowsSixPlusThreeTimesWaveFormula() {
    val world = TownWorld()
    val engine = GameEngine(world, null)
    assertEquals(1, engine.wave)
    assertEquals(9, engine.waveTarget) // 6 + 3 * 1

    val save = engine.toSurvivorSave("u1", "Tester")
    engine.applyCloudSave(save.copy(wave = 4L))
    assertEquals(18, engine.waveTarget) // 6 + 3 * 4
  }

  @Test
  fun backpackWeightLimitEnforcedAndPackExpandsCapacity() {
    val world = TownWorld()
    val engine = GameEngine(world, null)
    assertEquals(25f, engine.computeMaxWeightKg(), 0.01f)

    // Equip Expedition Pack (+5 kg)
    assertTrue(engine.addItemToBackpack("hiking_pack", 1))
    val idx = engine.backpack.indexOfFirst { it.itemId == "hiking_pack" }
    engine.useBackpackSlot(idx)
    assertEquals(EquipmentSlot.BACK to "hiking_pack", EquipmentSlot.BACK to engine.equipment[EquipmentSlot.BACK])
    assertEquals(30f, engine.computeMaxWeightKg(), 0.01f)

    // Trying to add excessive weight fails gracefully
    assertFalse(engine.addItemToBackpack("m4_carbine", 20))
  }

  @Test
  fun weaponReloadTransfersAmmoFromBackpack() {
    val world = TownWorld()
    val engine = GameEngine(world, null)
    engine.m4Mag = 10
    val beforeReserve = engine.countBackpackItem("ammo_556")
    engine.activeHotbarSlot = 0 // m4_carbine
    engine.startReload()
    assertTrue(engine.isReloading)

    // Advance 1.6 seconds to finish reload
    repeat(40) { engine.step(0.04f) }
    assertFalse(engine.isReloading)
    assertEquals(30, engine.m4Mag)
    assertEquals(beforeReserve - 20, engine.countBackpackItem("ammo_556"))
  }

  @Test
  fun starvationDrainsHealthOverTime() {
    val world = TownWorld()
    val engine = GameEngine(world, null)
    engine.food = 0f
    engine.water = 0f
    val initialHp = engine.health
    repeat(20) { engine.step(0.05f) }
    assertTrue("Expected health to drop from starvation", engine.health < initialHp)
  }

  @Test
  fun playerCanWalkInsideTiendaDoorwayAndCollideWithWalls() {
    val world = TownWorld()
    // Tienda is x in [-22, -10], z in [-10, 2], door on East wall at x=-10, z=-4
    val pos = floatArrayOf(-10f, -4f)
    world.resolveCircleCollision(pos, 0.42f)
    assertEquals(-10f, pos[0], 0.05f)
    assertEquals(-4f, pos[1], 0.05f)

    // Pushing into solid north wall at x=-16, z=-10 pushes player outside
    val wallPos = floatArrayOf(-16f, -9.9f)
    world.resolveCircleCollision(wallPos, 0.42f)
    assertTrue(wallPos[1] <= -10.3f || wallPos[1] >= -9.1f)
  }
}
