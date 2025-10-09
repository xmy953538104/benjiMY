package org.kenjinx.android

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.kenjinx.android.viewmodels.QuickSettings

class PhysicalControllerManager(val activity: MainActivity) {
    private var controllerId: Int = -1

    fun onKeyEvent(event: KeyEvent): Boolean {
        // Make sure we are connected
        if (controllerId == -1) {
            controllerId = KenjinxNative.inputConnectGamepad(0)
        }

        val id = getGamePadButtonInputId(event.keyCode)
        if (id != GamePadButtonInputId.None) {
            val isNotFallback = (event.flags and KeyEvent.FLAG_FALLBACK) == 0
            // Many gamepads send additional fallback events – we suppress them.
            if (isNotFallback) {
                when (event.action) {
                    KeyEvent.ACTION_UP -> {
                        KenjinxNative.inputSetButtonReleased(id.ordinal, controllerId)
                    }
                    KeyEvent.ACTION_DOWN -> {
                        KenjinxNative.inputSetButtonPressed(id.ordinal, controllerId)
                    }
                }
            }
            return true
        }

        return false
    }

    fun onMotionEvent(ev: MotionEvent) {
        if (ev.action == MotionEvent.ACTION_MOVE) {
            if (controllerId == -1) {
                controllerId = KenjinxNative.inputConnectGamepad(0)
            }

            val leftStickX = ev.getAxisValue(MotionEvent.AXIS_X)
            val leftStickY = ev.getAxisValue(MotionEvent.AXIS_Y)
            val rightStickX = ev.getAxisValue(MotionEvent.AXIS_Z)
            val rightStickY = ev.getAxisValue(MotionEvent.AXIS_RZ)

            KenjinxNative.inputSetStickAxis(1, leftStickX, -leftStickY, controllerId)
            KenjinxNative.inputSetStickAxis(2, rightStickX, -rightStickY, controllerId)

            ev.device?.apply {
                if (sources and InputDevice.SOURCE_DPAD != InputDevice.SOURCE_DPAD) {
                    // Controller uses HAT instead of “real” DPAD
                    val dPadHor = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
                    val dPadVert = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)

                    if (dPadVert == 0.0f) {
                        KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadUp.ordinal, controllerId)
                        KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadDown.ordinal, controllerId)
                    }
                    if (dPadHor == 0.0f) {
                        KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadLeft.ordinal, controllerId)
                        KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadRight.ordinal, controllerId)
                    }

                    if (dPadVert < 0.0f) {
                        KenjinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadUp.ordinal, controllerId)
                        KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadDown.ordinal, controllerId)
                    }
                    if (dPadHor < 0.0f) {
                        KenjinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadLeft.ordinal, controllerId)
                        KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadRight.ordinal, controllerId)
                    }

                    if (dPadVert > 0.0f) {
                        KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadUp.ordinal, controllerId)
                        KenjinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadDown.ordinal, controllerId)
                    }
                    if (dPadHor > 0.0f) {
                        KenjinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadLeft.ordinal, controllerId)
                        KenjinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadRight.ordinal, controllerId)
                    }
                }
            }
        }
    }

    fun connect(): Int {
        controllerId = KenjinxNative.inputConnectGamepad(0)
        return controllerId
    }

    fun disconnect() {
        controllerId = -1
    }

    private fun getGamePadButtonInputId(keycode: Int): GamePadButtonInputId {
        val quickSettings = QuickSettings(activity)
        return when (keycode) {
            // ABXY (Switch/Xbox layout switchable)
            KeyEvent.KEYCODE_BUTTON_A -> if (!quickSettings.useSwitchLayout) GamePadButtonInputId.A else GamePadButtonInputId.B
            KeyEvent.KEYCODE_BUTTON_B -> if (!quickSettings.useSwitchLayout) GamePadButtonInputId.B else GamePadButtonInputId.A
            KeyEvent.KEYCODE_BUTTON_X -> if (!quickSettings.useSwitchLayout) GamePadButtonInputId.X else GamePadButtonInputId.Y
            KeyEvent.KEYCODE_BUTTON_Y -> if (!quickSettings.useSwitchLayout) GamePadButtonInputId.Y else GamePadButtonInputId.X

            // Shoulder buttons
            KeyEvent.KEYCODE_BUTTON_L1 -> GamePadButtonInputId.LeftShoulder
            KeyEvent.KEYCODE_BUTTON_L2 -> GamePadButtonInputId.LeftTrigger
            KeyEvent.KEYCODE_BUTTON_R1 -> GamePadButtonInputId.RightShoulder
            KeyEvent.KEYCODE_BUTTON_R2 -> GamePadButtonInputId.RightTrigger

            // **L3 / R3 (Stick-Click) – CORRECT: *_Button**
            KeyEvent.KEYCODE_BUTTON_THUMBL -> GamePadButtonInputId.LeftStickButton
            KeyEvent.KEYCODE_BUTTON_THUMBR -> GamePadButtonInputId.RightStickButton

            // Additional fallback keycodes for some pads (optional)
            KeyEvent.KEYCODE_BUTTON_11 -> GamePadButtonInputId.LeftStickButton   // isolated L3
            KeyEvent.KEYCODE_BUTTON_12 -> GamePadButtonInputId.RightStickButton  // isolated R3

            // D-Pad
            KeyEvent.KEYCODE_DPAD_UP -> GamePadButtonInputId.DpadUp
            KeyEvent.KEYCODE_DPAD_DOWN -> GamePadButtonInputId.DpadDown
            KeyEvent.KEYCODE_DPAD_LEFT -> GamePadButtonInputId.DpadLeft
            KeyEvent.KEYCODE_DPAD_RIGHT -> GamePadButtonInputId.DpadRight

            // Plus/Minus
            KeyEvent.KEYCODE_BUTTON_START -> GamePadButtonInputId.Plus
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamePadButtonInputId.Minus

            else -> GamePadButtonInputId.None
        }
    }
}
