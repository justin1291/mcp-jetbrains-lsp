
/**
 * Base entity class
 */
abstract class BaseEntity {
    private long lastModified;

    protected void setLastModified(long timestamp) {
        this.lastModified = timestamp;
    }

    public long getLastModified() {
        return lastModified;
    }
}
