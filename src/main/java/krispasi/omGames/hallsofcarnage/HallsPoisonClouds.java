package krispasi.omGames.hallsofcarnage;

import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

final class HallsPoisonClouds {
    private HallsPoisonClouds() {
    }

    static void spawn(JavaPlugin plugin,
                      World world,
                      Entity source,
                      Location center,
                      double radius,
                      int durationTicks,
                      int pulseIntervalTicks,
                      PotionEffectType effectType,
                      int effectTicks,
                      int amplifier,
                      double damage,
                      Predicate<LivingEntity> targetPredicate) {
        if (plugin == null || world == null || center == null || !world.equals(center.getWorld())
                || effectType == null || targetPredicate == null) {
            return;
        }
        double safeRadius = Math.max(0.5, radius);
        int safeDuration = Math.max(5, durationTicks);
        int interval = Math.max(5, pulseIntervalTicks);
        int safeEffectTicks = Math.max(20, effectTicks);
        Location anchor = center.clone();
        render(world, anchor, safeRadius, effectType, 0);
        apply(world, source, anchor, safeRadius, effectType, safeEffectTicks, Math.max(0, amplifier), damage, targetPredicate);
        BukkitRunnable task = new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!world.equals(anchor.getWorld()) || ticks > safeDuration) {
                    cancel();
                    return;
                }
                render(world, anchor, safeRadius, effectType, ticks);
                if (ticks % 20 == 0) {
                    apply(world, source, anchor, safeRadius, effectType, safeEffectTicks, Math.max(0, amplifier), damage, targetPredicate);
                }
                ticks += interval;
            }
        };
        task.runTaskTimer(plugin, 1L, interval);
    }

    private static void render(World world, Location center, double radius, PotionEffectType effectType, int ticks) {
        double pulse = 0.55 + Math.sin(ticks / 8.0) * 0.15;
        Location waist = center.clone().add(0.0, 0.8, 0.0);
        Location ground = center.clone().add(0.0, 0.2, 0.0);
        if (effectType == PotionEffectType.WITHER) {
            world.spawnParticle(Particle.SMOKE, waist, 95, radius * pulse, 0.5, radius * pulse, 0.03);
            world.spawnParticle(Particle.LARGE_SMOKE, ground, 28, radius * 0.35, 0.16, radius * 0.35, 0.015);
            world.spawnParticle(Particle.SCULK_SOUL, waist, 12, radius * 0.45, 0.35, radius * 0.45, 0.02);
        } else {
            world.spawnParticle(Particle.WITCH, waist, 70, radius * pulse, 0.45, radius * pulse, 0.02);
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, waist, 58, radius * 0.5, 0.4, radius * 0.5, 0.025);
            world.spawnParticle(Particle.SMOKE, ground, 36, radius * 0.45, 0.18, radius * 0.45, 0.01);
            world.spawnParticle(Particle.CLOUD, ground, 20, radius * 0.35, 0.12, radius * 0.35, 0.01);
        }
        if (ticks == 0) {
            world.playSound(center, Sound.ENTITY_SPLASH_POTION_BREAK, 0.65f, effectType == PotionEffectType.WITHER ? 0.55f : 0.8f);
        }
    }

    private static void apply(World world,
                              Entity source,
                              Location center,
                              double radius,
                              PotionEffectType effectType,
                              int effectTicks,
                              int amplifier,
                              double damage,
                              Predicate<LivingEntity> targetPredicate) {
        double radiusSquared = radius * radius;
        for (Entity nearby : world.getNearbyEntities(center, radius, Math.max(2.0, radius), radius)) {
            if (nearby instanceof LivingEntity living
                    && living.getLocation().distanceSquared(center) <= radiusSquared
                    && targetPredicate.test(living)) {
                living.addPotionEffect(new PotionEffect(effectType, effectTicks, amplifier, true, true, true));
                if (damage > 0.0) {
                    living.damage(damage, source);
                }
            }
        }
    }
}
