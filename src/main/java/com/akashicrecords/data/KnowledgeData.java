package com.akashicrecords.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Знання одного гравця: бали Знань і набір вивчених предметів.
 * Зберігається як приєднані дані (attachment) гравця й копіюється після смерті.
 */
public class KnowledgeData {
    public static final MapCodec<KnowledgeData> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.optionalFieldOf("points", 0L).forGetter(KnowledgeData::getPoints),
            ResourceLocation.CODEC.listOf().optionalFieldOf("learned", List.of()).forGetter(KnowledgeData::learnedList),
            Codec.BOOL.optionalFieldOf("demon", false).forGetter(KnowledgeData::hasDemon)
    ).apply(inst, KnowledgeData::new));

    private long points;
    private boolean demon;
    private final Set<ResourceLocation> learned = new LinkedHashSet<>();

    public KnowledgeData() {
    }

    public KnowledgeData(long points, List<ResourceLocation> learned, boolean demon) {
        this.points = Math.max(0L, points);
        this.learned.addAll(learned);
        this.demon = demon;
    }

    /** Чи з'їв гравець Яблуко Лапласа (відкрито меню «Демон Лапласа»). */
    public boolean hasDemon() {
        return demon;
    }

    public void setDemon(boolean value) {
        this.demon = value;
    }

    public long getPoints() {
        return points;
    }

    public List<ResourceLocation> learnedList() {
        return new ArrayList<>(learned);
    }

    public boolean hasLearned(ResourceLocation id) {
        return learned.contains(id);
    }

    /** @return true, якщо предмет вивчено вперше */
    public boolean learn(ResourceLocation id) {
        return learned.add(id);
    }

    public void addPoints(long amount) {
        if (amount <= 0) {
            return;
        }
        long sum = points + amount;
        points = sum < 0 ? Long.MAX_VALUE : sum; // захист від переповнення
    }

    /** @return true, якщо балів вистачило й вони списані */
    public boolean spend(long amount) {
        if (amount < 0 || amount > points) {
            return false;
        }
        points -= amount;
        return true;
    }
}
