package com.otw.adminapi.medical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otw.adminapi.common.api.ApiException;
import com.otw.adminapi.common.util.DateTimeFormats;
import com.otw.adminapi.connector.ConnectorConfigEntity;
import com.otw.adminapi.connector.ConnectorConfigRepository;
import com.otw.adminapi.connector.ConnectorService;
import com.otw.adminapi.connector.CryptoService;
import com.otw.adminapi.security.AuthenticatedUser;
import com.otw.adminapi.sync.SyncTaskEntity;
import com.otw.adminapi.sync.SyncTaskRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class MedicalReportService {
  private static final Logger log = LoggerFactory.getLogger(MedicalReportService.class);
  private static final int SESSION_TTL_HOURS = 24;
  private static final String MEDICAL_SOURCE_STREAM = "medical_report";
  private static final byte[] PDF_MAGIC = new byte[] {0x25, 0x50, 0x44, 0x46}; // %PDF

  private static final Map<String, List<String>> SECTION_ITEM_KEYS = Map.ofEntries(
    Map.entry("general", List.of("height", "weight", "bmi", "pulse_rate", "sbp", "dbp")),
    Map.entry("internal_medicine", List.of(
      "past_medical_history", "thoracic_contour", "heart_rate", "heart_rhythm", "heart_sounds", "cardiac_murmur",
      "pulmonary_auscultation", "abdominal_wall", "abdominal_tenderness", "liver", "gallbladder", "spleen",
      "kidneys", "neurological_system"
    )),
    Map.entry("surgery", List.of("skin", "spine", "extremity_joints", "thyroid_gland", "superficial_lymph_nodes", "breast_exam", "other_findings")),
    Map.entry("ophthalmology", List.of("ucva_left", "ucva_right", "bcva_left", "bcva_right", "color_vision", "external_eye")),
    Map.entry("ent", List.of(
      "auricle", "external_auditory_canal", "tympanic_membrane", "mastoid", "external_nose",
      "nasal_cavity", "nasal_vestibule", "nasal_septum", "paranasal_sinuses", "oropharynx"
    )),
    Map.entry("cbc", List.of(
      "wbc", "neut_abs", "lymph_abs", "mono_abs", "eos_abs", "baso_abs", "neut_pct", "lymph_pct", "mono_pct",
      "eos_pct", "baso_pct", "rbc", "hgb", "hct", "mcv", "mch", "mchc", "rdw_sd", "nrbc_abs", "nrbc_pct",
      "plt", "pdw", "mpv", "pct", "p_lcr"
    )),
    Map.entry("liver_function", List.of("tbil", "ibil", "dbil", "alt", "ast", "ast_alt", "tp", "alb", "glob", "ag_ratio", "ggt", "alp")),
    Map.entry("kidney_function", List.of()),
    Map.entry("ecg", List.of("routine_ecg")),
    Map.entry("imaging", List.of("chest_dr_pa"))
  );

  private static final Map<String, CellValue> MOCK_VALUE_OVERRIDES = Map.ofEntries(
    Map.entry("general.height", new CellValue("172", "165-185", "cm", "")),
    Map.entry("general.weight", new CellValue("68.5", "50-80", "kg", "")),
    Map.entry("general.bmi", new CellValue("23.1", "18.5-23.9", "kg/m^2", "")),
    Map.entry("general.pulse_rate", new CellValue("72", "60-100", "bpm", "")),
    Map.entry("general.sbp", new CellValue("122", "90-139", "mmHg", "")),
    Map.entry("general.dbp", new CellValue("78", "60-89", "mmHg", "")),
    Map.entry("internal_medicine.past_medical_history", new CellValue("No significant history", "", "", "")),
    Map.entry("internal_medicine.heart_rate", new CellValue("72", "60-100", "bpm", "")),
    Map.entry("ophthalmology.ucva_left", new CellValue("4.9", ">=4.8", "", "")),
    Map.entry("ophthalmology.ucva_right", new CellValue("5.0", ">=4.8", "", "")),
    Map.entry("cbc.wbc", new CellValue("6.10", "3.5-9.5", "10^9/L", "")),
    Map.entry("cbc.neut_abs", new CellValue("3.60", "1.8-6.3", "10^9/L", "")),
    Map.entry("cbc.lymph_abs", new CellValue("1.90", "1.1-3.2", "10^9/L", "")),
    Map.entry("cbc.rbc", new CellValue("4.90", "4.3-5.8", "10^12/L", "")),
    Map.entry("cbc.hgb", new CellValue("148", "130-175", "g/L", "")),
    Map.entry("cbc.plt", new CellValue("226", "125-350", "10^9/L", "")),
    Map.entry("liver_function.tbil", new CellValue("12.6", "5.0-21.0", "umol/L", "")),
    Map.entry("liver_function.alt", new CellValue("22", "9-50", "U/L", "")),
    Map.entry("liver_function.ast", new CellValue("21", "15-40", "U/L", "")),
    Map.entry("ecg.routine_ecg", new CellValue("Sinus rhythm", "Sinus rhythm", "", "")),
    Map.entry("imaging.chest_dr_pa", new CellValue("No active pulmonary lesion", "", "", ""))
  );

  private final ConnectorService connectorService;
  private final ConnectorConfigRepository connectorConfigRepository;
  private final CryptoService cryptoService;
  private final MedicalReportWorkerClient medicalReportWorkerClient;
  private final MedicalReportParseSessionRepository parseSessionRepository;
  private final SyncTaskRepository syncTaskRepository;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ZoneId zoneId;

  public MedicalReportService(
    ConnectorService connectorService,
    ConnectorConfigRepository connectorConfigRepository,
    CryptoService cryptoService,
    MedicalReportWorkerClient medicalReportWorkerClient,
    MedicalReportParseSessionRepository parseSessionRepository,
    SyncTaskRepository syncTaskRepository,
    JdbcTemplate jdbcTemplate,
    ObjectMapper objectMapper,
    @Value("${app.timezone}") String timezone
  ) {
    this.connectorService = connectorService;
    this.connectorConfigRepository = connectorConfigRepository;
    this.cryptoService = cryptoService;
    this.medicalReportWorkerClient = medicalReportWorkerClient;
    this.parseSessionRepository = parseSessionRepository;
    this.syncTaskRepository = syncTaskRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.zoneId = ZoneId.of(timezone);
  }

  public MedicalReportParseResponse parseReport(
    AuthenticatedUser authenticatedUser,
    String recordNumber,
    String reportDateValue,
    String institution,
    MultipartFile file
  ) {
    ConnectorConfigEntity connector = connectorService.requireConfiguredConnector(authenticatedUser, ConnectorService.MEDICAL_REPORT_ID);
    Map<String, String> connectorConfig = cryptoService.decrypt(connector.getConfigCiphertext());
    String provider = normalizeProvider(connectorConfig.get("provider"));
    String modelId = normalizeRequired(connectorConfig.get("modelId"), "Model ID is required.");
    String apiKey = normalizeRequired(connectorConfig.get("apiKey"), "API Key is required.");
    if (apiKey.length() < 8) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_NOT_CONFIGURED", "Please configure provider/model/api key first.");
    }

    String normalizedRecordNumber = normalizeRequired(recordNumber, "Record number is required.");
    LocalDate reportDate = parseDate(reportDateValue, "Report date must use YYYY-MM-DD format.");
    String normalizedInstitution = normalizeRequired(institution, "Medical institution is required.");
    byte[] fileBytes = validatePdfFile(file);
    String fileName = normalizeRequired(file.getOriginalFilename(), "Medical report file is required.");
    String fileHash = sha256Hex(fileBytes);
    Instant parsedAt = Instant.now();
    String fileBase64 = Base64.getEncoder().encodeToString(fileBytes);

    MedicalReportWorkerClient.ParsedPayload parsedPayload = medicalReportWorkerClient.parseReport(
      new MedicalReportWorkerClient.WorkerParseRequest(
        authenticatedUser.accountId(),
        connector.getId(),
        Map.of("provider", provider, "modelId", modelId, "apiKey", apiKey),
        normalizedRecordNumber,
        reportDate.toString(),
        normalizedInstitution,
        fileName,
        fileBase64
      )
    );

    List<MedicalReportParseResponse.SectionView> sections = mergeParsedSections(parsedPayload.sections());

    MedicalReportParseSessionEntity session = new MedicalReportParseSessionEntity();
    session.setAccountId(authenticatedUser.accountId());
    session.setConnectorConfigId(connector.getId());
    session.setProvider(provider);
    session.setModelId(modelId);
    session.setRecordNumber(normalizedRecordNumber);
    session.setReportDate(reportDate);
    session.setInstitution(normalizedInstitution);
    session.setFileName(fileName);
    session.setFileHash(fileHash);
    session.setParsedPayloadJsonb(writeJson(Map.of("sections", sections)));
    session.setStatus("parsed");
    session.setExpiresAt(parsedAt.plusSeconds(SESSION_TTL_HOURS * 3600L));
    parseSessionRepository.save(session);

    return new MedicalReportParseResponse(
      session.getId(),
      ConnectorService.MEDICAL_REPORT_ID,
      provider,
      modelId,
      DateTimeFormats.formatNullable(parsedAt, zoneId),
      sections
    );
  }

  public MedicalReportSyncResponse syncReport(AuthenticatedUser authenticatedUser, MedicalReportSyncRequest request) {
    ConnectorConfigEntity connector = connectorService.requireConfiguredConnector(authenticatedUser, ConnectorService.MEDICAL_REPORT_ID);
    UUID parseSessionId = parseUuid(request.parseSessionId(), "Invalid parseSessionId.");
    MedicalReportParseSessionEntity session = parseSessionRepository.findByIdAndAccountId(parseSessionId, authenticatedUser.accountId())
      .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARSE_SESSION", "Parse session is invalid or expired."));

    Instant now = Instant.now();
    if (session.getConsumedAt() != null || session.getExpiresAt().isBefore(now)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARSE_SESSION", "Parse session is invalid or expired.");
    }
    if (!"parsed".equalsIgnoreCase(normalizeOptional(session.getStatus()))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARSE_SESSION", "Parse session is invalid or expired.");
    }

    String recordNumber = normalizeRequired(request.recordNumber(), "Record number is required.");
    LocalDate reportDate = parseDate(request.reportDate(), "Report date must use YYYY-MM-DD format.");
    String institution = normalizeRequired(request.institution(), "Medical institution is required.");
    String fileName = normalizeRequired(request.fileName(), "Medical report file name is required.");
    List<NormalizedSectionPayload> normalizedSections = normalizeSections(request.sections());

    Instant windowStart = reportDate.atStartOfDay(zoneId).toInstant();
    Instant windowEnd = reportDate.plusDays(1).atStartOfDay(zoneId).toInstant();
    Instant sourceRecordAt = reportDate.atStartOfDay(zoneId).toInstant();

    SyncTaskEntity task = new SyncTaskEntity();
    task.setAccountId(authenticatedUser.accountId());
    task.setConnectorConfigId(connector.getId());
    task.setParseSessionId(parseSessionId);
    task.setTriggerType("manual");
    task.setStatus("running");
    task.setWindowStartAt(windowStart);
    task.setWindowEndAt(windowEnd);
    task.setStartedAt(now);
    task.setDispatchedAt(now);
    task = syncTaskRepository.saveAndFlush(task);

    try {
      List<Map<String, Object>> sections = normalizedSections.stream()
        .map(NormalizedSectionPayload::toMap)
        .toList();
      String payloadJson = writeJson(Map.of(
        "parseSessionId", session.getId().toString(),
        "provider", session.getProvider(),
        "modelId", session.getModelId(),
        "recordNumber", recordNumber,
        "reportDate", reportDate.toString(),
        "institution", institution,
        "fileName", fileName,
        "sections", sections
      ));
      String payloadHash = sha256Hex(payloadJson.getBytes(StandardCharsets.UTF_8));
      session.setStatus("confirmed");
      session.setConfirmedPayloadJsonb(payloadJson);
      parseSessionRepository.save(session);

      UpsertAction action = upsertRawMedicalReport(
        authenticatedUser.accountId(),
        connector.getId(),
        task.getId(),
        recordNumber,
        reportDate,
        sourceRecordAt,
        now,
        payloadHash,
        payloadJson
      );

      task.setStatus("success");
      task.setFinishedAt(Instant.now());
      task.setFetchedCount(1);
      task.setInsertedCount(action == UpsertAction.INSERTED ? 1 : 0);
      task.setUpdatedCount(action == UpsertAction.UPDATED ? 1 : 0);
      task.setUnchangedCount(action == UpsertAction.UNCHANGED ? 1 : 0);
      task.setDedupedCount(action == UpsertAction.UNCHANGED ? 1 : 0);
      task.setErrorCode(null);
      task.setErrorMessage(null);
      task = syncTaskRepository.save(task);

      connector.setLastRunAt(task.getFinishedAt());
      connectorConfigRepository.save(connector);

      session.setStatus("synced");
      session.setConsumedAt(Instant.now());
      parseSessionRepository.save(session);

      return new MedicalReportSyncResponse(
        task.getId(),
        ConnectorService.MEDICAL_REPORT_ID,
        task.getStatus(),
        task.getTriggerType(),
        DateTimeFormats.formatNullableOrNull(task.getWindowStartAt(), zoneId),
        DateTimeFormats.formatNullableOrNull(task.getWindowEndAt(), zoneId),
        DateTimeFormats.formatNullableOrNull(task.getStartedAt(), zoneId),
        DateTimeFormats.formatNullableOrNull(task.getFinishedAt(), zoneId),
        task.getFetchedCount(),
        task.getInsertedCount(),
        task.getUpdatedCount(),
        task.getDedupedCount(),
        task.getErrorMessage(),
        DateTimeFormats.formatNullableOrNull(task.getCreatedAt(), zoneId)
      );
    } catch (ApiException exception) {
      log.warn("medical report sync failed with business error code={} message={}", exception.getCode(), exception.getMessage());
      markTaskFailed(task, exception.getCode(), exception.getMessage());
      throw exception;
    } catch (Exception exception) {
      log.error("medical report sync failed unexpectedly", exception);
      markTaskFailed(task, "MEDICAL_REPORT_SYNC_FAILED", exception.getMessage());
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDICAL_REPORT_SYNC_FAILED", "Unable to sync medical report.");
    }
  }

  private void markTaskFailed(SyncTaskEntity task, String errorCode, String errorMessage) {
    task.setStatus("failed");
    task.setFinishedAt(Instant.now());
    task.setErrorCode(truncate(errorCode, 64));
    task.setErrorMessage(errorMessage);
    syncTaskRepository.save(task);
  }

  private UpsertAction upsertRawMedicalReport(
    UUID accountId,
    UUID connectorConfigId,
    UUID syncTaskId,
    String externalId,
    LocalDate reportDate,
    Instant sourceRecordAt,
    Instant sourceUpdatedAt,
    String payloadHash,
    String payloadJson
  ) {
    String selectSql = """
      SELECT payload_hash
      FROM raw.health_snapshot_record
      WHERE account_id = ?
        AND connector_config_id = ?
        AND source_stream = ?
        AND source_record_date = ?
        AND external_id = ?
      """;

    List<String> existingHashes = jdbcTemplate.query(
      selectSql,
      (rs, rowNum) -> rs.getString("payload_hash"),
      accountId,
      connectorConfigId,
      MEDICAL_SOURCE_STREAM,
      reportDate,
      externalId
    );

    if (existingHashes.isEmpty()) {
      String insertSql = """
        INSERT INTO raw.health_snapshot_record (
          account_id,
          connector_config_id,
          sync_task_id,
          connector_id,
          source_stream,
          external_id,
          source_record_date,
          source_record_at,
          source_updated_at,
          payload_hash,
          collected_at,
          payload_jsonb
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
        """;

      jdbcTemplate.update(
        insertSql,
        accountId,
        connectorConfigId,
        syncTaskId,
        ConnectorService.MEDICAL_REPORT_ID,
        MEDICAL_SOURCE_STREAM,
        externalId,
        reportDate,
        toOffsetDateTime(sourceRecordAt),
        toOffsetDateTime(sourceUpdatedAt),
        payloadHash,
        toOffsetDateTime(Instant.now()),
        payloadJson
      );
      return UpsertAction.INSERTED;
    }

    if (payloadHash.equals(existingHashes.get(0))) {
      return UpsertAction.UNCHANGED;
    }

    String updateSql = """
      UPDATE raw.health_snapshot_record
      SET sync_task_id = ?,
          connector_id = ?,
          source_record_at = ?,
          source_updated_at = ?,
          payload_hash = ?,
          collected_at = ?,
          payload_jsonb = CAST(? AS jsonb),
          updated_at = NOW()
        WHERE account_id = ?
        AND connector_config_id = ?
        AND source_stream = ?
        AND source_record_date = ?
        AND external_id = ?
      """;

    jdbcTemplate.update(
      updateSql,
      syncTaskId,
      ConnectorService.MEDICAL_REPORT_ID,
      toOffsetDateTime(sourceRecordAt),
      toOffsetDateTime(sourceUpdatedAt),
      payloadHash,
      toOffsetDateTime(Instant.now()),
      payloadJson,
      accountId,
      connectorConfigId,
      MEDICAL_SOURCE_STREAM,
      reportDate,
      externalId
    );

    return UpsertAction.UPDATED;
  }

  private List<NormalizedSectionPayload> normalizeSections(List<MedicalReportSyncRequest.MedicalReportSectionInput> sections) {
    if (sections == null) {
      return List.of();
    }

    return sections.stream()
      .map(section -> {
        String normalizedExamDate = normalizeOptional(section.examDate());
        if (!normalizedExamDate.isBlank()) {
          normalizedExamDate = parseDate(normalizedExamDate, "Section exam date must use YYYY-MM-DD format.").toString();
        }

        return new NormalizedSectionPayload(
          normalizeRequired(section.sectionKey(), "Section key is required."),
          normalizeOptional(section.examiner()),
          normalizedExamDate,
          normalizeItems(section.items())
        );
      })
      .collect(Collectors.toList());
  }

  private List<Map<String, Object>> normalizeItems(List<MedicalReportSyncRequest.MedicalReportItemInput> items) {
    if (items == null) {
      return List.of();
    }

    return items.stream()
      .map(item -> Map.<String, Object>of(
        "itemKey", normalizeRequired(item.itemKey(), "Item key is required."),
        "result", normalizeOptional(item.result()),
        "referenceValue", normalizeOptional(item.referenceValue()),
        "unit", normalizeOptional(item.unit()),
        "abnormalFlag", normalizeOptional(item.abnormalFlag())
      ))
      .collect(Collectors.toList());
  }

  private List<MedicalReportParseResponse.SectionView> buildMockSections() {
    List<MedicalReportParseResponse.SectionView> sections = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : SECTION_ITEM_KEYS.entrySet()) {
      String sectionKey = entry.getKey();
      List<MedicalReportParseResponse.ItemView> items = entry.getValue().stream()
        .map(itemKey -> {
          CellValue value = MOCK_VALUE_OVERRIDES.getOrDefault(sectionKey + "." + itemKey, CellValue.defaultValue());
          return new MedicalReportParseResponse.ItemView(
            itemKey,
            value.result(),
            value.referenceValue(),
            value.unit(),
            value.abnormalFlag()
          );
        })
        .toList();
      sections.add(new MedicalReportParseResponse.SectionView(sectionKey, "", "", items));
    }
    return sections;
  }

  private List<MedicalReportParseResponse.SectionView> mergeParsedSections(List<MedicalReportWorkerClient.ParsedSection> parsedSections) {
    if (parsedSections == null || parsedSections.isEmpty()) {
      return buildMockSections();
    }

    Map<String, MedicalReportWorkerClient.ParsedSection> sectionLookup = parsedSections.stream()
      .collect(Collectors.toMap(
        section -> normalizeRequired(section.sectionKey(), "Section key is required."),
        section -> section,
        (left, right) -> right
      ));

    List<MedicalReportParseResponse.SectionView> sections = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : SECTION_ITEM_KEYS.entrySet()) {
      String sectionKey = entry.getKey();
      MedicalReportWorkerClient.ParsedSection parsedSection = sectionLookup.get(sectionKey);
      Map<String, MedicalReportWorkerClient.ParsedItem> parsedItems = parsedSection == null
        ? Map.of()
        : (parsedSection.items() == null ? List.<MedicalReportWorkerClient.ParsedItem>of() : parsedSection.items()).stream()
          .collect(Collectors.toMap(
            item -> normalizeRequired(item.itemKey(), "Item key is required."),
            item -> item,
            (left, right) -> right
          ));

      List<MedicalReportParseResponse.ItemView> items = entry.getValue().stream()
        .map(itemKey -> {
          MedicalReportWorkerClient.ParsedItem parsed = parsedItems.get(itemKey);
          if (parsed == null) {
            CellValue defaultValue = MOCK_VALUE_OVERRIDES.getOrDefault(sectionKey + "." + itemKey, CellValue.defaultValue());
            return new MedicalReportParseResponse.ItemView(
              itemKey,
              defaultValue.result(),
              defaultValue.referenceValue(),
              defaultValue.unit(),
              defaultValue.abnormalFlag()
            );
          }

          return new MedicalReportParseResponse.ItemView(
            itemKey,
            normalizeOptional(parsed.result()),
            normalizeOptional(parsed.referenceValue()),
            normalizeOptional(parsed.unit()),
            normalizeOptional(parsed.abnormalFlag())
          );
        })
        .toList();

      sections.add(new MedicalReportParseResponse.SectionView(
        sectionKey,
        parsedSection == null ? "" : normalizeOptional(parsedSection.examiner()),
        parsedSection == null ? "" : normalizeOptional(parsedSection.examDate()),
        items
      ));
    }

    return sections;
  }

  private String normalizeProvider(String value) {
    String provider = normalizeRequired(value, "Provider is required.").toLowerCase(Locale.ROOT);
    if (!List.of("deepseek", "volcengine").contains(provider)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTOR_NOT_CONFIGURED", "Please configure provider/model/api key first.");
    }
    return provider;
  }

  private LocalDate parseDate(String value, String message) {
    try {
      return LocalDate.parse(normalizeRequired(value, message));
    } catch (DateTimeParseException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
  }

  private byte[] validatePdfFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Medical report file is required.");
    }

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unable to read uploaded file.");
    }

    if (bytes.length < PDF_MAGIC.length) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "Only PDF files are supported.");
    }
    for (int index = 0; index < PDF_MAGIC.length; index++) {
      if (bytes[index] != PDF_MAGIC[index]) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "Only PDF files are supported.");
      }
    }

    return bytes;
  }

  private UUID parseUuid(String value, String message) {
    try {
      return UUID.fromString(normalizeRequired(value, message));
    } catch (IllegalArgumentException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON_SERIALIZATION_ERROR", "Unable to serialize medical report payload.");
    }
  }

  private String sha256Hex(byte[] value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value));
    } catch (Exception exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "HASH_ERROR", "Unable to hash payload.");
    }
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }

  private OffsetDateTime toOffsetDateTime(Instant value) {
    return value == null ? null : OffsetDateTime.ofInstant(value, zoneId);
  }

  private String normalizeRequired(String value, String message) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
    return normalized;
  }

  private String normalizeOptional(String value) {
    return value == null ? "" : value.trim();
  }

  private record NormalizedSectionPayload(
    String sectionKey,
    String examiner,
    String examDate,
    List<Map<String, Object>> items
  ) {
    private Map<String, Object> toMap() {
      return Map.of(
        "sectionKey", sectionKey,
        "examiner", examiner,
        "examDate", examDate,
        "items", items
      );
    }
  }

  private enum UpsertAction {
    INSERTED,
    UPDATED,
    UNCHANGED
  }

  private record CellValue(
    String result,
    String referenceValue,
    String unit,
    String abnormalFlag
  ) {
    static CellValue defaultValue() {
      return new CellValue("Normal", "", "", "");
    }
  }
}
