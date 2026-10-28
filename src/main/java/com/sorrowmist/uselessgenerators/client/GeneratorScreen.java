package com.sorrowmist.uselessgenerators.client;

import com.sorrowmist.uselessgenerators.GeneratorMenu;
import com.sorrowmist.uselessgenerators.PortMode;
import com.sorrowmist.uselessgenerators.ReactorLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import com.sorrowmist.uselesscore.client.ui.*;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import java.util.List;
import java.util.Optional;

public final class GeneratorScreen extends FittedContainerScreen<GeneratorMenu> {
    private static final int TEXT = 0xffedf3fa;
    private static final int MUTED = 0xffb9c7d4;
    private final MachineButton[] ports = new MachineButton[12];
    private MachineButton statusTab, configTab, structureTab, recheck;
    private boolean configuration;
    private boolean structure;
    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 286; imageHeight = 240;
        inventoryLabelX = 62; inventoryLabelY = 146; titleLabelX = 8; titleLabelY = 7;
    }
    @Override protected void init() {
        boolean oldStructure=structure;
        super.init();
        statusTab = addRenderableWidget(new MachineButton(leftPos + 10, topPos + 24, 78, 18,
                Component.translatable("gui.useless_generators.status_tab"), button -> setPage(false)));
        configTab = addRenderableWidget(new MachineButton(leftPos + 92, topPos + 24, 78, 18,
                Component.translatable("gui.useless_generators.ports_tab"), button -> setPage(true)));
        structureTab = addRenderableWidget(new MachineButton(leftPos + 174, topPos + 24, 100, 18,
                Component.translatable("gui.useless_generators.structure_tab"), button -> {
                    setPage(false); structure = true; statusTab.setSelected(false); structureTab.setSelected(true); recheck.visible = true;
                }));
        recheck = addRenderableWidget(new MachineButton(leftPos + 174, topPos + 119, 94, 18,
                Component.translatable("gui.useless_generators.recheck"), button -> send(GeneratorMenu.RECHECK_ACTION)));
        for (int i = 0; i < 12; i++) {
            final int action = i;
            ports[i] = addRenderableWidget(new MachineButton(leftPos + 64 + (i % 6) * 35,
                    topPos + (i < 6 ? 76 : 110), 32, 18, Component.empty(), button -> send(action)));
        }
        setPage(configuration);if(oldStructure){structure=true;statusTab.setSelected(false);structureTab.setSelected(true);recheck.visible=true;}refreshButtons();
    }
    private void setPage(boolean configuration) {
        structure = false; structureTab.setSelected(false); recheck.visible = false;
        this.configuration = configuration;
        statusTab.setSelected(!configuration); configTab.setSelected(configuration);
        for (MachineButton port : ports) port.visible = configuration;
    }
    private void send(int action) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }
    private void refreshButtons() {
        structureTab.visible = menu.isReactor();
        configTab.visible = !menu.isReactor();
        for (int i = 0; i < 12; i++) {
            Direction side = Direction.values()[i % 6]; PortMode mode = menu.mode(i < 6, side);
            ports[i].setMessage(Component.translatable(mode.translation()));
            Component hint = Component.translatable("gui.useless_generators.port_hint",
                    Component.translatable("gui.useless_generators.direction." + side.getName()),
                    Component.translatable(i < 6 ? "gui.useless_generators.fluid" : "gui.useless_generators.energy"),
                    Component.translatable(mode.translation()));
            if (side == menu.front()) hint = hint.copy().append("\n").append(Component.translatable("gui.useless_generators.front_face"));
            if (side == menu.front().getOpposite()) hint = hint.copy().append("\n").append(Component.translatable("gui.useless_generators.rear_face"));
            ports[i].setTooltip(Tooltip.create(hint));
        }
    }
    @Override protected void containerTick() { super.containerTick(); refreshButtons(); }
    private void inset(GuiGraphics g, int x, int y, int width, int height) {
        UiDraw.panel(g,leftPos+x,topPos+y,width,height,8,UiTheme.DARK.surface(),UiTheme.DARK.border());
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        UiDraw.glass(g,leftPos,topPos,imageWidth,imageHeight,12,UiTheme.DARK);
        if (configuration || structure) {
            inset(g, 10, 46, 266, 96);
        } else {
            inset(g, 10, 46, 46, 96); inset(g, 60, 46, 216, 96);
            UiDraw.rounded(g,leftPos+15,topPos+53,36,69,6,UiTheme.DARK.inset());
            var fuel = menu.fuel();
            if (!fuel.isEmpty() && minecraft != null) {
                var extensions = IClientFluidTypeExtensions.of(fuel.getFluid());
                var sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(extensions.getStillTexture(fuel));
                int fillHeight = Math.max(0, Math.min(66, (int) Math.ceil(66.0 * fuel.getAmount() / 16000)));
                int color = extensions.getTintColor(fuel);
                g.setColor(((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f, (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
                g.enableScissor(leftPos + 17, topPos + 120 - fillHeight, leftPos + 49, topPos + 120);
                for (int x = 17; x < 49; x += 16) for (int y = 54; y < 120; y += 16) g.blit(leftPos + x, topPos + y, 0, 16, 16, sprite);
                g.disableScissor(); g.setColor(1, 1, 1, 1);
            } else {
                g.drawString(font, "+", leftPos + 30, topPos + 83, 0xffc5bcd1, false);
            }
            UiDraw.rounded(g,leftPos+66,topPos+130,204,8,4,UiTheme.DARK.inset());
            int fill = menu.capacity() <= 0 ? 0 : Math.max(0, Math.min(202, (int) (202.0 * menu.stored() / menu.capacity())));
            UiDraw.gradient(g,leftPos+67,topPos+131,fill,6,3,0xff64ceec,0xff328efa,0,0,0x4057b8ff,1);
        }
        for (var slot : menu.slots) {
            UiDraw.slot(g,leftPos+slot.x-1,topPos+slot.y-1,18,UiTheme.DARK,false);
        }
    }
    private static String amount(long value) {
        if (value < 1000) return Long.toString(value);
        String[] suffix = {"", "k", "M", "G", "T", "P", "E"}; double number = value; int index = 0;
        while (number >= 1000 && index < suffix.length - 1) { number /= 1000; index++; }
        return String.format(java.util.Locale.ROOT, "%.2f%s", number, suffix[index]);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 7, TEXT, false);
        Component facing = Component.translatable("gui.useless_generators.facing",
                Component.translatable("gui.useless_generators.direction." + menu.front().getName()));
        g.drawString(font, facing, imageWidth - font.width(facing) - 8, 7, TEXT, false);
        Component ae = Component.translatable(menu.aeConnected() ? "message.useless_generators.ae_connected" : "message.useless_generators.ae_disconnected");
        if (!menu.isReactor()) g.drawString(font, ae, imageWidth - font.width(ae) - 12, 29, MUTED, false);
        if (structure) {
            var status = menu.structureStatus();
            Component state = Component.translatable("gui.useless_generators.structure." + status.name().toLowerCase(java.util.Locale.ROOT));
            g.drawString(font, font.plainSubstrByWidth(state.getString(), 250), 18, 53, TEXT, false);
            g.drawString(font, "80 x 80 x 32 | 5 x 5 chunks", 18, 68, MUTED, false);
            var origin = menu.structureOrigin();
            g.drawString(font, Component.translatable("gui.useless_generators.origin", origin.getX(), origin.getY(), origin.getZ()), 18, 83, TEXT, false);
            if (menu.expectedPart() >= 0) {
                var pos = menu.structureProblem();
                g.drawString(font, Component.translatable("gui.useless_generators.missing", pos.getX(), pos.getY(), pos.getZ()), 18, 98, TEXT, false);
                var expected = ReactorLayout.Part.values()[Math.min(ReactorLayout.Part.values().length - 1, menu.expectedPart())];
                Component part = expected == ReactorLayout.Part.AIR ? Component.translatable("gui.useless_generators.clear_space")
                        : com.sorrowmist.uselessgenerators.ReactorStructure.block(expected).getName();
                g.drawString(font, font.plainSubstrByWidth(part.getString(), 148), 18, 123, MUTED, false);
            } else {
                g.drawString(font, menu.structureChecked() + " / " + ReactorLayout.CELLS.size(), 18, 98, TEXT, false);
                g.drawString(font, ae, 18, 123, MUTED, false);
            }
        } else if (configuration) {
            g.drawString(font, Component.translatable("gui.useless_generators.fluid"), 18, 81, TEXT, false);
            g.drawString(font, Component.translatable("gui.useless_generators.energy"), 18, 115, TEXT, false);
            for (Direction side : Direction.values()) {
                Component direction = Component.translatable("gui.useless_generators.direction." + side.getName());
                g.drawString(font, direction, 80 + side.ordinal() * 35 - font.width(direction) / 2, 58, TEXT, false);
            }
        } else {
            var fuel = menu.fuel();
            Component fuelName = fuel.isEmpty() ? Component.translatable("message.useless_generators.empty_fuel") : fuel.getDisplayName();
            g.drawString(font, font.plainSubstrByWidth(fuelName.getString(), 202), 66, 51, TEXT, false);
            g.drawString(font, String.format(java.util.Locale.ROOT, "%.2f / 16.00 B", fuel.getAmount() / 1000.0), 66, 67, MUTED, false);
            g.drawString(font, Component.translatable("gui.useless_generators.maximum", amount(menu.maximumRate())), 66, 83, TEXT, false);
            g.drawString(font, Component.translatable("gui.useless_generators.generating", amount(menu.generated())), 66, 99, TEXT, false);
            g.drawString(font, amount(menu.stored()) + " / " + amount(menu.capacity()) + " FE", 66, 115, TEXT, false);
            Component action = menu.isReactor() ? Component.literal("ME") : Component.translatable("gui.useless_generators.tank_action");
            g.drawString(font, action, 33 - font.width(action) / 2, 128, MUTED, false);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
    }
    @Override protected boolean clickContent(double x, double y, int button) {
        if (!menu.isReactor() && !configuration && !structure && button == 0 && isHovering(15, 53, 36, 69, x, y) && !menu.getCarried().isEmpty()) {
            send(GeneratorMenu.CONTAINER_ACTION); return true;
        }
        return super.clickContent(x, y, button);
    }
    @Override protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g); super.renderContent(g, mouseX, mouseY, partial); renderTooltip(g, mouseX, mouseY);
        if (!configuration && !structure && isHovering(15, 53, 36, 69, mouseX, mouseY)) {
            var fuel = menu.fuel();
            g.renderTooltip(font, List.of(fuel.isEmpty() ? Component.translatable("message.useless_generators.empty_fuel") : fuel.getDisplayName(),
                    Component.literal(fuel.getAmount() + " / 16000 mB"),
                    Component.translatable("gui.useless_generators.remaining", Long.toString(menu.fuelRemainder())),
                    Component.translatable(menu.isReactor() ? "gui.useless_generators.me.hatch_hint" : "gui.useless_generators.bucket_hint")), Optional.empty(), mouseX, mouseY);
        } else if (!configuration && !structure && isHovering(66, 130, 204, 8, mouseX, mouseY)) {
            g.renderTooltip(font, Component.literal(menu.stored() + " / " + menu.capacity() + " FE"), mouseX, mouseY);
        }
    }
}
