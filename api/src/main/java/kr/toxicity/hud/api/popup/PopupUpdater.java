package kr.toxicity.hud.api.popup;

/**
 * Represents popup's updater.
 */
public interface PopupUpdater {
    /**
     * Tries to update.
     * @return whether updating is success
     */
    boolean update();

    /**
     * Keeps this popup alive without restarting its entrance animation.
     * @return whether keep alive succeeded
     */
    default boolean keepAlive() {
        return update();
    }

    /**
     * Gets index.
     * @return index
     */
    int getIndex();

    /**
     * Sets index priority.
     * @param index new index
     */
    void setIndex(int index);

    /**
     * Removes this popup.
     */
    void remove();
}
