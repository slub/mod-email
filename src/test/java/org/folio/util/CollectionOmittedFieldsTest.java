package org.folio.util;

import static org.folio.services.MailSettingsService.SMTP_CONFIG_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class CollectionOmittedFieldsTest {

  @Test
  public void smtpConfigurationHasConnectionSettingsAndCredentialsAsCollectionOmittedFields() {
    assertEquals(Set.of("host", "port", "username", "password", "ssl", "trustAll", "loginOption",
        "startTlsOptions", "expirationHours", "authMethods", "idleTimeout", "connectTimeout"),
      CollectionOmittedFields.forKey(SMTP_CONFIG_KEY));
  }

  @Test
  public void unknownKeyHasNoCollectionOmittedFields() {
    assertTrue(CollectionOmittedFields.forKey("unknown").isEmpty());
    assertTrue(CollectionOmittedFields.forKey(null).isEmpty());
  }

  @Test
  public void stripRemovesCollectionOmittedFieldsOnly() {
    Map<String, Object> value = new HashMap<>(Map.of("host", "localhost", "port", 25,
      "ssl", false, "password", "secret", "from", "noreply@folio.org"));
    CollectionOmittedFields.strip(SMTP_CONFIG_KEY, value);
    assertEquals(Map.of("from", "noreply@folio.org"), value);
  }

  @Test
  public void stripIgnoresNonMapValues() {
    CollectionOmittedFields.strip(SMTP_CONFIG_KEY, "not a map");
  }
}
