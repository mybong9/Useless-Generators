package com.sorrowmist.uselessgenerators.client;

import com.sorrowmist.uselesscore.client.ui.UiButton;
import net.minecraft.network.chat.Component;

/** Shared animated glass controls for both generator screens. */
final class MachineButton extends UiButton {
    MachineButton(int x,int y,int width,int height,Component label,OnPress action){super(x,y,width,height,label,action);}
    void setSelected(boolean selected){selected(selected);}
}
