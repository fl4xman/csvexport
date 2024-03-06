package com.csvexport;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.Tile;
import net.runelite.api.Scene;
import net.runelite.api.coords.WorldPoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import net.runelite.api.*;
import com.google.inject.Provides;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.eventbus.Subscribe;
import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(
		name = "CSV Export"
)
public class CSVExport extends Plugin {

	@Inject
	private Client client;

	@Inject
	private CSVExportConfig config;

	@Inject
	private ClientThread clientThread;

	private BufferedWriter writer;
	private BufferedWriter inventoryWriter;
	private BufferedWriter tileObjectWriter;
	private BufferedWriter itemsWriter;

	private String latestMessage;

	@Provides
	CSVExportConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CSVExportConfig.class);
	}

	private void initializeFileWriters() {
		String outputFolderPath = Paths.get(config.SaveDir(), "osrs_stats").toString();
		Path outputFolder = Paths.get(outputFolderPath);

		if (!Files.exists(outputFolder)) {
			try {
				Files.createDirectories(outputFolder);
			} catch (IOException e) {
				log.error("Failed to create output directory: {}", outputFolderPath, e);
				return;
			}
		}
		String currentDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
		String dataFileName = String.format("%s/player_data_%s.csv", outputFolderPath, currentDate);
		String inventoryFileName = String.format("%s/item_data_%s.csv", outputFolderPath, currentDate);
		String tileObjectFileName = String.format("%s/tile_object_data_%s.csv", outputFolderPath, currentDate);
		String itemsFileName = String.format("%s/items_%s.csv", outputFolderPath, currentDate);

		File outputDataFile = new File(dataFileName);
		File inventoryDataFile = new File(inventoryFileName);
		File tileObjectDataFile = new File(tileObjectFileName);
		File itemsDataFile = new File(itemsFileName);

		try {
			writer = new BufferedWriter(new FileWriter(outputDataFile));
			inventoryWriter = new BufferedWriter(new FileWriter(inventoryDataFile));
			tileObjectWriter = new BufferedWriter(new FileWriter(tileObjectDataFile));
			itemsWriter = new BufferedWriter(new FileWriter(itemsDataFile));

			log.info("Writers initialized: {}, {}, {} and {}", dataFileName, inventoryFileName, tileObjectFileName, itemsFileName);

			writer.write("timestamp,type,variable,value\n");
			writer.flush();

			inventoryWriter.write("timestamp,type,item_id,quantity\n");
			inventoryWriter.flush();

			tileObjectWriter.write("timestamp,name,price,xp,yp\n");
			tileObjectWriter.flush();

			itemsWriter.write("timestamp,item_id,quantity\n");
			itemsWriter.flush();
		} catch (IOException e) {
			log.error("Failed to initialize file writers", e);
		}
	}

	@Override
	protected void startUp() throws Exception {
		super.startUp();
		initializeFileWriters();
	}

	@Override
	protected void shutDown() throws Exception {
		if (writer != null) {
			writer.close();
		}
		if (inventoryWriter != null) {
			inventoryWriter.close();
		}
		if (tileObjectWriter != null) {
			tileObjectWriter.close();
		}
		if (itemsWriter != null) {
			itemsWriter.close();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		latestMessage = event.getMessage();
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		try {
			writeGameData();
		} catch (IOException e) {
			log.error("Error writing game data to CSV: {}", e.getMessage());
		}
	}

	private void writeGameData() throws IOException {
		LocalDateTime currentTime = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		String timestamp = currentTime.format(formatter);

		for (Skill skill : Skill.values()) {
			writeRow(writer, timestamp, "skill", skill.getName(), String.valueOf(client.getRealSkillLevel(skill)));
			writeRow(writer, timestamp, "skill", skill.getName() + "_boosted", String.valueOf(client.getBoostedSkillLevel(skill)));
			writeRow(writer, timestamp, "skill", skill.getName() + "_xp", String.valueOf(client.getSkillExperience(skill)));
		}

		Player player = client.getLocalPlayer();
		Actor npc = player.getInteracting();
		String npcName = npc != null ? npc.getName() : "null";


		for (NPC eachNpc : client.getNpcs()) {
			String name = Objects.requireNonNull(eachNpc.getName()).toLowerCase();
			writeRow(writer, timestamp, "local_npcs", "name", name);
		}

		writeGroundItems();

		writeInventory(InventoryID.INVENTORY);
		writeInventory(InventoryID.EQUIPMENT);

		writeRow(writer, timestamp, "status", "active_prayer", player.getOverheadIcon() != null ? player.getOverheadIcon().name().toLowerCase() : "null");
		writeRow(writer, timestamp, "status", "animation", String.valueOf(player.getAnimation()));
		writeRow(writer, timestamp, "status", "animation_pose", String.valueOf(player.getPoseAnimation()));
		writeRow(writer, timestamp, "status", "last_message", latestMessage);
		writeRow(writer, timestamp, "status", "spec_energy", String.valueOf(client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10));
		writeRow(writer, timestamp, "status", "run_energy", String.valueOf(client.getEnergy() / 100));
		writeRow(writer, timestamp, "status", "weight", String.valueOf(client.getWeight()));
		writeRow(writer, timestamp, "status", "game_tick", String.valueOf(client.getGameCycle()));
		writeRow(writer, timestamp, "status", "health", String.valueOf(client.getBoostedSkillLevel(Skill.HITPOINTS)));
		writeRow(writer, timestamp, "status", "prayer", String.valueOf(client.getBoostedSkillLevel(Skill.PRAYER)));
		writeRow(writer, timestamp, "status", "interacting_code", String.valueOf(player.getInteracting()));
		writeRow(writer, timestamp, "status", "target_name", npcName);

		WorldPoint playerLocation = player.getWorldLocation();
		writeRow(writer, timestamp, "positional", "player_x", String.valueOf(playerLocation.getX()));
		writeRow(writer, timestamp, "positional", "player_y", String.valueOf(playerLocation.getY()));
		writeRow(writer, timestamp, "positional", "player_plane", String.valueOf(playerLocation.getPlane()));
		writeRow(writer, timestamp, "positional", "player_regionID", String.valueOf(playerLocation.getRegionID()));
		writeRow(writer, timestamp, "positional", "player_regionX", String.valueOf(playerLocation.getRegionX()));
		writeRow(writer, timestamp, "positional", "player_regionY", String.valueOf(playerLocation.getRegionY()));

	}

	private void writeGroundItems() throws IOException {
		LocalDateTime currentTime = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		String timestamp = currentTime.format(formatter);

		LocalPoint localPoint = client.getLocalPlayer().getLocalLocation();
		int sceneX = localPoint.getSceneX();
		int sceneY = localPoint.getSceneY();
		int plane = client.getPlane();

		Scene scene = client.getScene();

		for (int offsetX = -25; offsetX <= 25; offsetX++) {
			for (int offsetY = -25; offsetY <= 25; offsetY++) {
				int tileX = sceneX + offsetX;
				int tileY = sceneY + offsetY;

				// Skip tiles outside the loaded map area
				if (tileX < 0 || tileY < 0 || tileX >= scene.getTiles()[plane].length || tileY >= scene.getTiles()[plane][tileX].length) {
					continue;
				}

				Tile sceneTile = scene.getTiles()[plane][tileX][tileY];
				if (sceneTile == null) {
					continue;
				}

				WorldPoint worldPoint = sceneTile.getWorldLocation();
				List<TileItem> groundItems = sceneTile.getGroundItems();
				if (groundItems != null) {
					for (TileItem groundItem : groundItems) {
						String itemName = client.getItemDefinition(groundItem.getId()).getName();
						int itemPrice = client.getItemDefinition(groundItem.getId()).getPrice();
						String yp = Arrays.toString(Perspective.getCanvasTilePoly(client, Objects.requireNonNull(LocalPoint.fromWorld(client, worldPoint))).ypoints);
						String xp = Arrays.toString(Perspective.getCanvasTilePoly(client, Objects.requireNonNull(LocalPoint.fromWorld(client, worldPoint))).xpoints);

						// Write tile object information to the file
						String row = String.format("%s,%s,%d,%s,%s\n", timestamp, itemName, itemPrice, xp, yp);
						tileObjectWriter.write(row);
						tileObjectWriter.flush();
					}
				}
			}
		}
	}

	private void writeInventory(InventoryID inventoryID) {
		LocalDateTime currentTime = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		String timestamp = currentTime.format(formatter);

		clientThread.invokeLater(() -> {
			ItemContainer itemContainer = client.getItemContainer(inventoryID);
			Item[] items = itemContainer != null ? itemContainer.getItems() : null;

			if (items != null) {
				try {
					for (Item item : items) {
						String row = String.format("%s,%s,%s,%s\n", timestamp, inventoryID.name(), item.getId(), item.getQuantity());
						inventoryWriter.write(row);
						inventoryWriter.flush();
					}
				} catch (IOException e) {
					log.error("Error writing inventory data: {}", e.getMessage());
				}
			} else {
				log.error("Failed to retrieve items from inventory.");
			}
		});
	}

	private void writeRow(BufferedWriter writer, String timestamp, String type, String variable, String value) throws IOException {
		if (writer != null) {
			writer.write(String.format("%s,%s,%s,%s\n", timestamp, type, variable, value));
			writer.flush();
		} else {
			log.error("BufferedWriter is null. Cannot write data.");
		}
	}
}