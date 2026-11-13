package com.akashicrecords.data;

import com.akashicrecords.AkashicRecords;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AkashicRecords.MODID);

    public static final Supplier<AttachmentType<KnowledgeData>> KNOWLEDGE = ATTACHMENTS.register("knowledge",
            () -> AttachmentType.builder(KnowledgeData::new)
                    .serialize(KnowledgeData.MAP_CODEC.codec())
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<StorageData>> STORAGE = ATTACHMENTS.register("storage",
            () -> AttachmentType.builder(() -> new StorageData())
                    .serialize(StorageData.CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
