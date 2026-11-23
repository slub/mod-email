package org.folio.rest.impl;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.folio.matchers.JsonMatchers.matchesJson;
import static org.folio.util.StubUtils.buildIncorrectWiserSmtpConfiguration;
import static org.folio.util.StubUtils.buildSmtpConfiguration;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.folio.rest.impl.base.AbstractAPITest;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class EmailSettingsAPITest extends AbstractAPITest {

  private static final String CONFIG_ID = UUID.randomUUID().toString();
  private static final List<String> SENSITIVE_FIELDS = List.of("host", "port", "username",
    "password", "ssl", "trustAll", "loginOption", "startTlsOptions", "expirationHours", "authMethods");

  @Test
  public void postEmailSettings_positive() {
    var emailSetting = buildValidEmailSettings();
    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, ignoredProperties()));
  }

  @Test
  public void postEmailSettings_settingWithSameIdCanBeCreatedOnce() {
    var emailSetting = buildValidEmailSettings();
    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, ignoredProperties()));

    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_BAD_REQUEST)
      .contentType(ContentType.TEXT)
      .body(startsWith("id value already exists in table settings"));
  }

  @Test
  public void postEmailSettings_settingWithSameKeyCanBeCreatedOnce() {
    var emailSetting = buildValidEmailSettings();
    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, List.of("id", "metadata")));

    var emailSetting2 = buildValidEmailSettings().put("id", UUID.randomUUID().toString());
    post(REST_PATH_MAIL_SETTINGS, emailSetting2.encodePrettily())
      .then()
      .statusCode(SC_BAD_REQUEST)
      .contentType(ContentType.TEXT)
      .body(is("lower(f_unaccent(jsonb ->> 'key'::text)) "
        + "value already exists in table settings: smtp-configuration"));
  }

  @Test
  public void postEmailSettings_negative_missingField() {
    var smtpConfiguration = buildSmtpConfiguration();
    smtpConfiguration.remove("host");
    var emailSetting = buildValidEmailSettings(CONFIG_ID, smtpConfiguration);

    var expectedError = new JsonObject()
      .put("message", "Invalid value in setting")
      .put("code", "validation_error")
      .put("parameters", new JsonArray()
        .add(new JsonObject().put("key", "value.host").put("value", "must not be null")));
    var expectedErrors = new JsonObject().put("errors", new JsonArray(List.of(expectedError)));

    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .contentType(ContentType.JSON)
      .body(matchesJson(expectedErrors));
  }

  @Test
  public void postEmailSettings_negative_unknowKeyIsRejected() {
    var emailSetting = buildValidEmailSettings().put("key", "new-smtp-configuration");

    var expectedError = new JsonObject()
      .put("message", "Key must be one of: [smtp-configuration]")
      .put("code", "validation_error")
      .put("parameters", new JsonArray()
        .add(new JsonObject().put("key", "key").put("value", "invalid value")));
    var expectedErrors = new JsonObject().put("errors", new JsonArray(List.of(expectedError)));

    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .contentType(ContentType.JSON)
      .body(matchesJson(expectedErrors));
  }

  @Test
  public void postEmailSettings_negative_withInvalidData() {
    JsonObject invalidEmailSetting = new JsonObject()
      .put("id", CONFIG_ID)
      .put("scope", "mod-email")
      .put("key", "smtp-configuration")
      .put("value", "test-value");

    var expectedError = new JsonObject()
      .put("message", "Invalid value in setting")
      .put("code", "validation_error")
      .put("parameters", new JsonArray()
        .add(new JsonObject().put("key", "value").put("value", "must be an object")));

    var expectedErrors = new JsonObject().put("errors", new JsonArray(List.of(expectedError)));

    post(REST_PATH_MAIL_SETTINGS, invalidEmailSetting.encodePrettily())
      .then()
      .contentType(ContentType.JSON)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body(matchesJson(expectedErrors));
  }

  @Test
  public void postEmailSettings_negative_withMissingScope() {
    JsonObject invalidEmailSetting = new JsonObject()
      .put("id", CONFIG_ID)
      .put("key", "smtp-configuration")
      .put("value", "test-value");

    var expectedError = new JsonObject()
      .put("message", "must not be null")
      .put("code", "jakarta.validation.constraints.NotNull.message")
      .put("type", "1")
      .put("parameters", new JsonArray()
        .add(new JsonObject().put("key", "scope").put("value", "null")));
    var expectedErrors = new JsonObject().put("errors", new JsonArray(List.of(expectedError)));

    post(REST_PATH_MAIL_SETTINGS, invalidEmailSetting.encodePrettily())
      .then()
      .contentType(ContentType.JSON)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body(matchesJson(expectedErrors));
  }

  @Test
  public void getEmailSettings_positive() {
    var emailSetting = buildValidEmailSettings();
    var createdSettingJsonString = post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, ignoredProperties()))
      .extract().body().asString();

    var postResponseJson = withoutSensitiveFields(new JsonObject(createdSettingJsonString));
    var emailSettings = new JsonObject()
      .put("settings", List.of(postResponseJson))
      .put("totalRecords", 1);

    get(REST_PATH_MAIL_SETTINGS)
      .then()
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSettings));
  }

  @Test
  public void getById_positive() {
    var emailSetting = buildValidEmailSettings();
    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, ignoredProperties()));

    var postResponseId = emailSetting.getString("id");

    get(emailSettingByIdPath(postResponseId))
      .then()
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting.put("id", postResponseId), ignoredProperties()));
  }

  @Test
  public void getEmailSettings_positive_querying() {
    var emailSetting = buildValidEmailSettings();
    var createdSettingJsonString = post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, ignoredProperties()))
      .extract().body().asString();

    var settingsCollection = new JsonObject()
      .put("settings", List.of(withoutSensitiveFields(new JsonObject(createdSettingJsonString))))
      .put("totalRecords", 1);

    get(REST_PATH_MAIL_SETTINGS + "?query=scope==mod-email")
      .then()
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .body(matchesJson(settingsCollection));

    get(REST_PATH_MAIL_SETTINGS + "?query=key==smtp-configuration")
      .then()
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .body(matchesJson(settingsCollection));
  }

  @Test
  public void getEmailSettings_positive_sensitiveFieldsAreOmitted() {
    var emailSetting = buildValidEmailSettings();
    emailSetting.getJsonObject("value").put("expirationHours", 24);
    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED);

    var value = getSettingValueFromCollection();
    SENSITIVE_FIELDS.forEach(field -> assertThat(field, value.containsKey(field), is(false)));
    var expectedValue = withoutSensitiveFields(emailSetting.copy()).getJsonObject("value");
    assertThat(value.encode(), matchesJson(expectedValue));

    get(emailSettingByIdPath(CONFIG_ID))
      .then()
      .statusCode(SC_OK)
      .body(matchesJson(emailSetting, ignoredProperties()));
  }

  @Test
  public void putById_positive_missingSensitiveFieldsKeepStoredValues() {
    var emailSetting = buildValidEmailSettings();
    emailSetting.getJsonObject("value").put("expirationHours", 24);
    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED);

    // a client that edits the collection GET result sends no sensitive fields
    var settingToPut = withoutSensitiveFields(emailSetting.copy());
    settingToPut.getJsonObject("value").put("from", "updated@folio.org");
    put(emailSettingByIdPath(CONFIG_ID), settingToPut.encodePrettily())
      .then()
      .statusCode(SC_NO_CONTENT);

    var expectedSetting = emailSetting.copy();
    expectedSetting.put("_version", 2);
    expectedSetting.getJsonObject("value").put("from", "updated@folio.org");
    get(emailSettingByIdPath(CONFIG_ID))
      .then()
      .statusCode(SC_OK)
      .body(matchesJson(expectedSetting, ignoredProperties()));
  }

  @Test
  public void putById_positive_newSensitiveValuesReplaceStoredOnes() {
    var emailSetting = buildValidEmailSettings();
    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED);

    var settingToPut = emailSetting.copy();
    settingToPut.getJsonObject("value")
      .put("password", "new-password")
      .put("host", "smtp.example.org")
      .put("port", 587)
      .put("ssl", false);
    put(emailSettingByIdPath(CONFIG_ID), settingToPut.encodePrettily())
      .then()
      .statusCode(SC_NO_CONTENT);

    get(emailSettingByIdPath(CONFIG_ID))
      .then()
      .statusCode(SC_OK)
      .body(matchesJson(settingToPut.copy().put("_version", 2), ignoredProperties()));
  }

  @Test
  public void getById_negative_entityNotFound() {
    get(emailSettingByIdPath(UUID.randomUUID().toString()))
      .then()
      .statusCode(SC_NOT_FOUND)
      .contentType(ContentType.TEXT)
      .body(is("Not found"));
  }

  @Test
  public void putById_positive() {
    var emailSetting = buildValidEmailSettings();
    var postResponse = post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, ignoredProperties()))
      .extract().response();

    var postResponseId = new JsonObject(postResponse.body().asString()).getString("id");
    var newValue = emailSetting.getJsonObject("value").put("from", "updated-reply-to@folio.org");
    var emailSettingToPut = emailSetting.copy().put("value", newValue);

    put(emailSettingByIdPath(postResponseId), emailSettingToPut.encodePrettily())
      .then()
      .statusCode(SC_NO_CONTENT);

    get(emailSettingByIdPath(postResponseId))
      .then()
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSettingToPut, List.of("_version", "metadata")));
  }

  @Test
  public void putById_negative_invalidKeyProvided() {
    var emailSetting = buildValidEmailSettings();
    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, ignoredProperties()))
      .extract().response();

    var emailSettingToPut = emailSetting.copy().put("key", "new-smtp-configuration");

    var expectedError = new JsonObject()
      .put("message", "Key must be one of: [smtp-configuration]")
      .put("code", "validation_error")
      .put("parameters", new JsonArray()
        .add(new JsonObject().put("key", "key").put("value", "invalid value")));
    var expectedErrors = new JsonObject().put("errors", new JsonArray(List.of(expectedError)));

    put(emailSettingByIdPath(CONFIG_ID), emailSettingToPut.encodePrettily())
      .then()
      .contentType(ContentType.JSON)
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .body(matchesJson(expectedErrors));
  }

  @Test
  public void putById_negative_invalidValueProvided() {
    var emailSetting = buildValidEmailSettings();
    var postResponse = post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, ignoredProperties()))
      .extract().response();

    var postResponseId = new JsonObject(postResponse.body().asString()).getString("id");
    // a missing sensitive field (e.g. host) is taken from the stored value, so use another violation
    var identity = new JsonObject().put("address", "noreply@folio.org");
    var newValue = emailSetting.getJsonObject("value").copy()
      .put("identities", new JsonArray().add(identity).add(identity.copy()));
    var emailSettingToPut = emailSetting.copy().put("value", newValue);

    var expectedError = new JsonObject()
      .put("message", "Invalid value in setting")
      .put("code", "validation_error")
      .put("parameters", new JsonArray()
        .add(new JsonObject().put("key", "value.identities")
          .put("value", "duplicate address: noreply@folio.org")));
    var expectedErrors = new JsonObject().put("errors", new JsonArray(List.of(expectedError)));

    put(emailSettingByIdPath(postResponseId), emailSettingToPut.encodePrettily())
      .then()
      .statusCode(SC_UNPROCESSABLE_ENTITY)
      .contentType(ContentType.JSON)
      .body(matchesJson(expectedErrors));
  }

  @Test
  public void putById_negative_idPathParameterIsWrong() {
    var emailSetting = buildValidEmailSettings();
    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, List.of("id", "metadata")));

    var emailSettingToPut = emailSetting.copy().put("id", CONFIG_ID);
    var idPathParameter = UUID.randomUUID().toString();

    put(emailSettingByIdPath(idPathParameter),
      emailSettingToPut.encodePrettily())
      .then()
      .statusCode(SC_NOT_FOUND);
  }

  @Test
  public void putById_negative_prevEntityNotFound() {
    var emailSetting = buildValidEmailSettings();
    var emailSettingToPut = emailSetting.copy().put("id", CONFIG_ID);
    var idPathParameter = UUID.randomUUID().toString();

    put(emailSettingByIdPath(idPathParameter),
      emailSettingToPut.encodePrettily())
      .then()
      .statusCode(SC_NOT_FOUND);
  }

  @Test
  public void deleteById_positive() {
    var emailSetting = buildValidEmailSettings();
    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, List.of("id", "metadata")));

    delete(emailSettingByIdPath(CONFIG_ID)).then().statusCode(SC_NO_CONTENT);
    get(emailSettingByIdPath(CONFIG_ID)).then().statusCode(SC_NOT_FOUND);
  }

  @Test
  public void deleteById_negative_idPathParameterIsWrong() {
    var emailSetting = buildValidEmailSettings();

    post(REST_PATH_MAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(SC_CREATED)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, List.of("id", "metadata")));

    delete(emailSettingByIdPath(UUID.randomUUID().toString()))
      .then()
      .statusCode(SC_NOT_FOUND);

    get(emailSettingByIdPath(emailSetting.getString("id")))
      .then()
      .statusCode(SC_OK)
      .contentType(ContentType.JSON)
      .body(matchesJson(emailSetting, ignoredProperties()));
  }

  private JsonObject getSettingValueFromCollection() {
    var body = get(REST_PATH_MAIL_SETTINGS + "?query=key==smtp-configuration")
      .then()
      .statusCode(SC_OK)
      .extract().body().asString();
    return new JsonObject(body).getJsonArray("settings").getJsonObject(0).getJsonObject("value");
  }

  private static JsonObject withoutSensitiveFields(JsonObject setting) {
    var value = setting.getJsonObject("value");
    SENSITIVE_FIELDS.forEach(value::remove);
    return setting;
  }

  private static String emailSettingByIdPath(String id) {
    return REST_PATH_MAIL_SETTINGS + "/" + id;
  }

  private static @NotNull List<String> ignoredProperties() {
    return List.of("metadata");
  }

  public static JsonObject buildIncorrectWiserEmailSettings() {
    return new JsonObject()
      .put("id", CONFIG_ID)
      .put("key", "smtp-configuration")
      .put("value", buildIncorrectWiserSmtpConfiguration())
      .put("scope", "mod-email")
      .put("_version", 1);
  }

  public static JsonObject buildValidEmailSettings() {
    return new JsonObject()
      .put("id", CONFIG_ID)
      .put("key", "smtp-configuration")
      .put("value", buildSmtpConfiguration())
      .put("scope", "mod-email")
      .put("_version", 1);
  }

  public static JsonObject buildValidEmailSettings(String id, JsonObject value) {
    return new JsonObject()
      .put("id", id)
      .put("key", "smtp-configuration")
      .put("value", value)
      .put("scope", "mod-email")
      .put("_version", 1);
  }
}

