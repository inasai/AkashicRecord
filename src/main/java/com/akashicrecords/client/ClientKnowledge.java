package com.akashicrecords.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Клієнтська копія Знань гравця (приходить із сервера). Не містить клієнтських класів Minecraft. */
public final class ClientKnowledge {
    private static long points;
    private static List<ResourceLocation> learned = List.of();
    private static Set<ResourceLocation> learnedSet = Set.of();
    private static int version;

    private ClientKnowledge() {
    }

    public static void set(long newPoints, List<ResourceLocation> newLearned) {
        points = newPoints;
        learned = List.copyOf(newLearned);
        learnedSet = new HashSet<>(newLearned);
        version++;
    }

    public static long points() {
        return points;
    }

    public static List<ResourceLocation> learned() {
        return learned;
    }

    public static boolean hasLearned(ResourceLocation id) {
        return learnedSet.contains(id);
    }

    public static int version() {
        return version;
    }
}
