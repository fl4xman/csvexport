package com.csvexport;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("csvexport")
public interface CSVExportConfig extends Config {

    @ConfigItem(
            keyName = "SaveDir",
            name = "Output Folder",
            description = "Directory path where the output data will be saved"
    )
    default String SaveDir() {
        return System.getProperty("user.home");

    };
    @ConfigItem(
            keyName = "MaxFileSize",
            name = "Max File Size",
            description = "Files greater than this size will be truncated. Set as 0 no truncation."
    )
    default Integer MaxFileSize() {
        return 0;

    };

}