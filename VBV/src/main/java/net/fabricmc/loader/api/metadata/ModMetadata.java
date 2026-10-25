package net.fabricmc.loader.api.metadata;

import java.util.Collection;
import net.fabricmc.loader.api.Version;

/** Small subset of Fabric's metadata API used by manual ports. */
public interface ModMetadata {
    String getId();
    String getName();
    String getDescription();
    Version getVersion();
    Collection<Person> getAuthors();
}
