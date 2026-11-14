package za.co.neroland.nerodecor.client.render.holo;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import za.co.neroland.nerodecor.content.block.holo.HoloSignBlockEntity;
import za.co.neroland.nerodecor.content.block.holo.HoloText;
import za.co.neroland.nerodecor.content.block.holo.net.HoloNet;

/** Two-line editor for a holo sign. "Done" sends one edit payload; the server re-validates and sanitises it. */
public class HoloSignEditScreen extends Screen {

    private final BlockPos pos;
    private final String first;
    private final String second;
    private EditBox line0;
    private EditBox line1;

    public HoloSignEditScreen(HoloSignBlockEntity sign) {
        super(Component.translatable("screen.nerodecor.holo_sign"));
        this.pos = sign.getBlockPos();
        this.first = sign.line(0);
        this.second = sign.line(1);
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 30;
        line0 = addRenderableWidget(new EditBox(font, x, y, 200, 20,
                Component.translatable("screen.nerodecor.holo_sign.line1")));
        line0.setMaxLength(HoloText.LINE_LENGTH);
        line0.setValue(first);
        line1 = addRenderableWidget(new EditBox(font, x, y + 24, 200, 20,
                Component.translatable("screen.nerodecor.holo_sign.line2")));
        line1.setMaxLength(HoloText.LINE_LENGTH);
        line1.setValue(second);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> done()).bounds(x, y + 54, 200, 20).build());
        setInitialFocus(line0);
    }

    private void done() {
        HoloNet.sendEdit(pos, HoloText.sanitise(line0.getValue(), HoloText.LINE_LENGTH),
                HoloText.sanitise(line1.getValue(), HoloText.LINE_LENGTH));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
