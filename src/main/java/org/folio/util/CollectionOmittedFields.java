package org.folio.util;

import static org.folio.services.MailSettingsService.SMTP_CONFIG_KEY;

import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fields of setting values that must not be returned by collection GETs, e.g. the SMTP server
 * connection settings and credentials.
 *
 * <p>A field is omitted when its property in the JSON schema of the setting value carries the
 * custom keyword {@code "x-omit-in-collection": true}. Each schema is read once from the classpath.</p>
 */
public final class CollectionOmittedFields {

  public static final String OMIT_IN_COLLECTION_KEYWORD = "x-omit-in-collection";

  private static final Logger log = LogManager.getLogger(CollectionOmittedFields.class);
  private static final Map<String, String> SCHEMAS_BY_SETTING_KEY = Map.of(
    SMTP_CONFIG_KEY, "ramls/smtp-configuration.json"
  );
  private static final Map<String, Set<String>> CACHE = new ConcurrentHashMap<>();

  private CollectionOmittedFields() {
    throw new IllegalStateException("Utility class cannot be initialized");
  }

  /**
   * Returns the names of the top-level fields omitted from collection GETs of the value of the given setting key.
   *
   * @param settingKey the setting key, e.g. {@code smtp-configuration}
   * @return the field names; empty for an unknown key
   */
  public static Set<String> forKey(String settingKey) {
    if (settingKey == null || !SCHEMAS_BY_SETTING_KEY.containsKey(settingKey)) {
      return Collections.emptySet();
    }
    return CACHE.computeIfAbsent(settingKey,
      key -> readCollectionOmittedProperties(SCHEMAS_BY_SETTING_KEY.get(key)));
  }

  /**
   * Removes the collection-omitted fields of the given setting key from the value map.
   *
   * @param settingKey the setting key
   * @param value      the setting value; ignored when it is not a map
   */
  public static void strip(String settingKey, Object value) {
    if (value instanceof Map<?, ?> map) {
      forKey(settingKey).forEach(map::remove);
    }
  }

  static Set<String> readCollectionOmittedProperties(String schemaPath) {
    try (InputStream in = CollectionOmittedFields.class.getClassLoader().getResourceAsStream(schemaPath)) {
      if (in == null) {
        throw new IllegalStateException("Schema not found on classpath: " + schemaPath);
      }
      var schema = new JsonObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      var properties = schema.getJsonObject("properties", new JsonObject());
      var result = new LinkedHashSet<String>();
      for (String name : properties.fieldNames()) {
        var property = properties.getValue(name);
        if (property instanceof JsonObject json && json.getBoolean(OMIT_IN_COLLECTION_KEYWORD, false)) {
          result.add(name);
        }
      }
      log.debug("readCollectionOmittedProperties:: schema: {}, omitted fields: {}", schemaPath, result);
      return Collections.unmodifiableSet(result);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read schema: " + schemaPath, e);
    }
  }
}
