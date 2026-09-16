package de.jangassen.platform.mac.convert;

import de.jangassen.jfa.FoundationCallback;
import de.jangassen.jfa.FoundationCallbackRegistry;
import de.jangassen.jfa.ObjcToJava;
import de.jangassen.jfa.appkit.NSControlStateValue;
import de.jangassen.jfa.appkit.NSEventModifierFlags;
import de.jangassen.jfa.appkit.NSMenuItem;
import de.jangassen.jfa.cleanup.NSCleaner;
import javafx.beans.property.BooleanProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.util.Optional;

public class MenuItemConverter {

  private static final String SEPARATOR_ITEM = "separatorItem";

  private MenuItemConverter() {
  }

  public static final FoundationCallback VOID_CALLBACK = new FoundationCallback(null, null);

  public static NSMenuItem convert(MenuItem menuItem) {
    if (menuItem == null) {
      return null;
    }

    if (menuItem instanceof SeparatorMenuItem) {
      return ObjcToJava.invokeStatic(NSMenuItem.class, SEPARATOR_ITEM);
    } else {
      return convertMenuItem(menuItem);
    }
  }

  private static NSMenuItem convertMenuItem(MenuItem menuItem) {
    FoundationCallback foundationCallback = getFoundationCallback(menuItem.getOnAction());

    NSMenuItem nsMenuItem = createNsMenuItem(menuItem, foundationCallback);
    menuItem.textProperty().addListener((observable, oldValue, newValue) ->
            nsMenuItem.setTitle(newValue)
    );

    menuItem.onActionProperty().addListener((observable, oldValue, newValue) ->
            updateAction(menuItem, nsMenuItem, newValue)
    );

    menuItem.acceleratorProperty().addListener((observable, oldValue, newValue) ->
            nsMenuItem.setKeyEquivalent(toKeyEquivalentString(newValue))
    );

    nsMenuItem.setKeyEquivalentModifierMask(toKeyEventModifierFlags(menuItem.getAccelerator()));
    menuItem.acceleratorProperty().addListener((observable, oldValue, newValue) ->
            nsMenuItem.setKeyEquivalentModifierMask(toKeyEventModifierFlags(newValue))
    );

    ImageConverter.convert(menuItem.getGraphic()).ifPresent(nsMenuItem::setImage);
    menuItem.graphicProperty().addListener((observable, oldValue, newValue) ->
            ImageConverter.convert(newValue).ifPresent(nsMenuItem::setImage)
    );

    // Both states are seeded as well as observed: an item is very often already disabled or hidden
    // by the time the bar is built, and a listener alone would not fire until it next changed.
    // Enabled requires NSMenu.setAutoenablesItems(false), which MenuConverter does.
    nsMenuItem.setEnabled(!menuItem.isDisable());
    menuItem.disableProperty().addListener((observable, oldValue, newValue) ->
            nsMenuItem.setEnabled(!newValue)
    );

    nsMenuItem.setHidden(!menuItem.isVisible());
    menuItem.visibleProperty().addListener((observable, oldValue, newValue) ->
            nsMenuItem.setHidden(!newValue)
    );

    convertSelectedState(menuItem, nsMenuItem);

    NSCleaner.register(menuItem, nsMenuItem);
    registerCallbackForCleanup(menuItem, foundationCallback);
    return nsMenuItem;
  }

  /**
   * Carries the check mark of an item that has one. Seeded as well as observed, for the same reason as
   * the enabled and hidden states above: an item is routinely already selected by the time the bar is
   * built, and a listener alone would not fire until the value next changed.
   *
   * <p>
   * Both selectable kinds are handled. They share no common supertype below {@link MenuItem} that
   * exposes the property, so the two cases are read separately and then treated alike.
   */
  private static void convertSelectedState(MenuItem menuItem, NSMenuItem nsMenuItem) {
    BooleanProperty selected = selectedProperty(menuItem);
    if (selected == null) {
      return;
    }

    nsMenuItem.setState(toControlStateValue(selected.get()));
    selected.addListener((observable, oldValue, newValue) ->
            nsMenuItem.setState(toControlStateValue(newValue))
    );
  }

  private static BooleanProperty selectedProperty(MenuItem menuItem) {
    if (menuItem instanceof CheckMenuItem) {
      return ((CheckMenuItem) menuItem).selectedProperty();
    }
    if (menuItem instanceof RadioMenuItem) {
      return ((RadioMenuItem) menuItem).selectedProperty();
    }
    return null;
  }

  private static int toControlStateValue(boolean selected) {
    return selected ? NSControlStateValue.NSControlStateValueOn : NSControlStateValue.NSControlStateValueOff;
  }

  private static void updateAction(MenuItem menuItem, NSMenuItem nsMenuItem, EventHandler<ActionEvent> eventHandler) {
    FoundationCallback foundationCallback = getFoundationCallback(eventHandler);
    nsMenuItem.setTarget(foundationCallback.getTarget());
    nsMenuItem.setAction(foundationCallback.getSelector());
    registerCallbackForCleanup(menuItem, foundationCallback);
  }

  private static void registerCallbackForCleanup(MenuItem menuItem, FoundationCallback foundationCallback) {
    if (foundationCallback != VOID_CALLBACK) {
      NSCleaner.register(menuItem, foundationCallback);
    }
  }

  private static NSMenuItem createNsMenuItem(MenuItem menuItem, FoundationCallback foundationCallback) {
    String text = Optional.ofNullable(menuItem.getText()).orElse("");
    NSMenuItem nsMenuItem = NSMenuItem.alloc()
            .initWithTitle(text, foundationCallback.getSelector(), toKeyEquivalentString(menuItem.getAccelerator()));
    nsMenuItem.setTarget(foundationCallback.getTarget());
    return nsMenuItem;
  }

  private static FoundationCallback getFoundationCallback(EventHandler<ActionEvent> action) {
    if (action == null) {
      return VOID_CALLBACK;
    }
    return FoundationCallbackRegistry.registerCallback(id -> action.handle(new ActionEvent()));
  }

  private static String toKeyEquivalentString(KeyCombination accelerator) {
    if (accelerator == null) {
      return "";
    }

    return keyEquivalent(accelerator);
  }

  private static String keyEquivalent(KeyCombination accelerator) {
    if (accelerator instanceof KeyCodeCombination) {
      return ((KeyCodeCombination) accelerator).getCode().getChar().toLowerCase();
    }

    return "";
  }

  private static int toKeyEventModifierFlags(KeyCombination accelerator) {
    int modifiers = 0;

    if (accelerator == null) {
      return modifiers;
    }

    if (accelerator.getShift() == KeyCombination.ModifierValue.DOWN) {
      modifiers |= NSEventModifierFlags.NSEventModifierFlagShift;
    }
    if (accelerator.getAlt() == KeyCombination.ModifierValue.DOWN) {
      modifiers |= NSEventModifierFlags.NSEventModifierFlagOption;
    }
    if (accelerator.getMeta() == KeyCombination.ModifierValue.DOWN) {
      modifiers |= NSEventModifierFlags.NSEventModifierFlagCommand;
    }
    if (accelerator.getControl() == KeyCombination.ModifierValue.DOWN) {
      modifiers |= NSEventModifierFlags.NSEventModifierFlagControl;
    }

    return modifiers;
  }
}
