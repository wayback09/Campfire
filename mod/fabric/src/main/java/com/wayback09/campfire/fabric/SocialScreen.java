package com.wayback09.campfire.fabric;

import com.wayback09.campfire.CampfireTheme;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Social & Friends screen for Campfire.
 * Saves and loads friends persistently from campfire/friends.json.
 */
public final class SocialScreen extends Screen {
    private final Screen parent;
    private final List<String> friends = new ArrayList<>();
    private final Path storagePath;
    private TextFieldWidget addFriendField;
    private String statusMsg = "";

    public SocialScreen(Screen parent) {
        super(Text.literal("Campfire Social"));
        this.parent = parent;
        this.storagePath = FabricLoader.getInstance().getGameDir().resolve("campfire/friends.json");
        loadFriends();
    }

    private void loadFriends() {
        friends.clear();
        if (Files.exists(storagePath)) {
            try {
                List<String> lines = Files.readAllLines(storagePath);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !friends.contains(trimmed)) {
                        friends.add(trimmed);
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    private void saveFriends() {
        try {
            Files.createDirectories(storagePath.getParent());
            Files.write(storagePath, friends);
        } catch (IOException ignored) {}
    }

    @Override
    protected void init() {
        // Friend username input
        addFriendField = new TextFieldWidget(textRenderer, width / 2 - 120, 50, 160, 20, Text.literal("Player Name"));
        addFriendField.setPlaceholder(Text.literal("Player username..."));
        addSelectableChild(addFriendField);

        // Add button
        addDrawableChild(ButtonWidget.builder(Text.literal("Add Friend"), b -> {
            String name = addFriendField.getText().trim();
            if (!name.isEmpty()) {
                if (!friends.contains(name)) {
                    friends.add(name);
                    saveFriends();
                    addFriendField.setText("");
                    statusMsg = "Added " + name + " to friends!";
                    clearAndInit();
                } else {
                    statusMsg = name + " is already in your friends list.";
                }
            }
        }).dimensions(width / 2 + 45, 50, 75, 20).build());

        // Dynamic friend list action buttons
        int listY = 105;
        for (int i = 0; i < friends.size(); i++) {
            if (listY > height - 60) break;
            final String friendName = friends.get(i);
            final int index = i;

            // Remove button [X]
            addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> {
                friends.remove(index);
                saveFriends();
                statusMsg = "Removed " + friendName;
                clearAndInit();
            }).dimensions(width / 2 + 95, listY - 2, 24, 18).build());

            listY += 22;
        }

        // Done button at bottom
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 100, height - 32, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(textRenderer, "Campfire Friends", width / 2, 22, CampfireTheme.TEXT);

        addFriendField.render(ctx, mouseX, mouseY, delta);

        if (!statusMsg.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMsg, width / 2, 75, CampfireTheme.ACCENT);
        }

        // Friends list header
        int listY = 90;
        ctx.drawTextWithShadow(textRenderer, "Friends (" + friends.size() + "):", width / 2 - 120, listY, CampfireTheme.ACCENT);
        listY += 15;

        if (friends.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No friends added yet.", width / 2, height / 2, 0xFFAAAAAA);
            ctx.drawCenteredTextWithShadow(textRenderer, "Enter a player's username above to connect!", width / 2, height / 2 + 14, 0xFF888888);
        } else {
            for (String f : friends) {
                if (listY > height - 60) break;
                // Card background
                ctx.fill(width / 2 - 120, listY - 2, width / 2 + 90, listY + 16, 0x55000000);
                ctx.drawTextWithShadow(textRenderer, "● " + f, width / 2 - 112, listY + 3, CampfireTheme.TEXT);
                listY += 22;
            }
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
