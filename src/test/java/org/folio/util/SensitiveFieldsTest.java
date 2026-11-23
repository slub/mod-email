package org.folio.util;

import static org.folio.services.MailSettingsService.SMTP_CONFIG_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class SensitiveFieldsTest {

  @Test
  public void smtpConfigurationHasPasswordAsSensitiveField() {
    assertEquals(Set.of("password"), SensitiveFields.forKey(SMTP_CONFIG_KEY));
  }

  @Test
  public void unknownKeyHasNoSensitiveFields() {
    assertTrue(SensitiveFields.forKey("unknown").isEmpty());
    assertTrue(SensitiveFields.forKey(null).isEmpty());
  }

  @Test
  public void stripRemovesSensitiveFieldsOnly() {
    Map<String, Object> value = new HashMap<>(Map.of("host", "localhost", "password", "secret"));
    SensitiveFields.strip(SMTP_CONFIG_KEY, value);
    assertEquals(Map.of("host", "localhost"), value);
  }

  @Test
  public void stripIgnoresNonMapValues() {
    SensitiveFields.strip(SMTP_CONFIG_KEY, "not a map");
  }
}
