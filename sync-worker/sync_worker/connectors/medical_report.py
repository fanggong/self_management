from __future__ import annotations

import base64
import json
import logging
import re
import time
from dataclasses import dataclass
from typing import Any

import requests

logger = logging.getLogger(__name__)

PDF_MAGIC = b"%PDF"
SUPPORTED_PROVIDERS = {"deepseek", "volcengine"}
PROVIDER_CHAT_COMPLETION_URLS = {
    "deepseek": "https://api.deepseek.com/chat/completions",
    "volcengine": "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
}
PROVIDER_RESPONSES_URLS = {
    "volcengine": "https://ark.cn-beijing.volces.com/api/v3/responses",
}
PROVIDER_FILES_URLS = {
    "volcengine": "https://ark.cn-beijing.volces.com/api/v3/files",
}
VOLCENGINE_FILE_UPLOAD_LIMIT_BYTES = 512 * 1024 * 1024
VOLCENGINE_FILE_POLL_INTERVAL_SECONDS = 2
VOLCENGINE_FILE_PROCESSING_TIMEOUT_SECONDS = 120
VOLCENGINE_RESPONSE_POLL_INTERVAL_SECONDS = 2
VOLCENGINE_RESPONSE_PROCESSING_TIMEOUT_SECONDS = 180
SECTION_ITEM_DEFINITIONS: dict[str, dict[str, Any]] = {
    "general": {
        "label": "一般常规检查",
        "items": [
            ("height", "身高"),
            ("weight", "体重"),
            ("bmi", "体重指数 / BMI"),
            ("pulse_rate", "脉搏 / Pulse"),
            ("sbp", "收缩压 / SBP"),
            ("dbp", "舒张压 / DBP"),
        ],
    },
    "internal_medicine": {
        "label": "内科常规检查",
        "items": [
            ("past_medical_history", "既往史 / 即往史"),
            ("thoracic_contour", "胸廓外形"),
            ("heart_rate", "心率"),
            ("heart_rhythm", "心律"),
            ("heart_sounds", "心音"),
            ("cardiac_murmur", "心脏杂音"),
            ("pulmonary_auscultation", "肺部听诊"),
            ("abdominal_wall", "腹壁"),
            ("abdominal_tenderness", "腹部压痛"),
            ("liver", "肝脏"),
            ("gallbladder", "胆囊"),
            ("spleen", "脾脏"),
            ("kidneys", "肾脏"),
            ("neurological_system", "神经系统"),
        ],
    },
    "surgery": {
        "label": "外科常规检查",
        "items": [
            ("skin", "皮肤"),
            ("spine", "脊柱"),
            ("extremity_joints", "四肢关节"),
            ("thyroid_gland", "甲状腺"),
            ("superficial_lymph_nodes", "浅表淋巴结"),
            ("breast_exam", "乳腺诊查"),
            ("other_findings", "其他"),
        ],
    },
    "ophthalmology": {
        "label": "眼科检查",
        "items": [
            ("ucva_left", "裸眼视力（左）"),
            ("ucva_right", "裸眼视力（右）"),
            ("bcva_left", "矫正视力（左）"),
            ("bcva_right", "矫正视力（右）"),
            ("color_vision", "色觉"),
            ("external_eye", "外眼"),
        ],
    },
    "ent": {
        "label": "耳鼻喉常规检查",
        "items": [
            ("auricle", "外耳廓"),
            ("external_auditory_canal", "外耳道"),
            ("tympanic_membrane", "鼓膜"),
            ("mastoid", "乳突"),
            ("external_nose", "鼻外部"),
            ("nasal_cavity", "鼻腔"),
            ("nasal_vestibule", "鼻前庭"),
            ("nasal_septum", "鼻中隔"),
            ("paranasal_sinuses", "鼻附窦"),
            ("oropharynx", "口咽部"),
        ],
    },
    "cbc": {
        "label": "血常规",
        "items": [
            ("wbc", "白细胞计数 / WBC"),
            ("neut_abs", "中性粒细胞绝对值 / NEUT#"),
            ("lymph_abs", "淋巴细胞绝对值 / LYMPH#"),
            ("mono_abs", "单核细胞绝对值 / MONO#"),
            ("eos_abs", "嗜酸细胞绝对值 / EOS#"),
            ("baso_abs", "嗜碱细胞绝对值 / BASO#"),
            ("neut_pct", "中性粒细胞百分比 / NEUT%"),
            ("lymph_pct", "淋巴细胞百分比 / LYMPH%"),
            ("mono_pct", "单核细胞百分比 / MONO%"),
            ("eos_pct", "嗜酸细胞百分比 / EOS%"),
            ("baso_pct", "嗜碱细胞百分比 / BASO%"),
            ("rbc", "红细胞计数 / RBC"),
            ("hgb", "血红蛋白 / HGB"),
            ("hct", "红细胞比容 / HCT"),
            ("mcv", "平均红细胞体积 / MCV"),
            ("mch", "平均红细胞血红蛋白量 / MCH"),
            ("mchc", "平均红细胞血红蛋白浓度 / MCHC"),
            ("rdw_sd", "平均红细胞分布宽度（SD）/ RDW-SD"),
            ("nrbc_abs", "有核红细胞计数 / NRBC#"),
            ("nrbc_pct", "有核红细胞比率 / NRBC%"),
            ("plt", "血小板计数 / PLT"),
            ("pdw", "血小板分布宽度 / PDW"),
            ("mpv", "血小板平均体积 / MPV"),
            ("pct", "血小板比积 / PCT"),
            ("p_lcr", "大血小板比率 / P-LCR"),
        ],
    },
    "liver_function": {
        "label": "肝功能",
        "items": [
            ("tbil", "总胆红素 / TBIL"),
            ("ibil", "间接胆红素 / IBIL"),
            ("dbil", "直接胆红素 / DBIL"),
            ("alt", "丙氨酸氨基转移酶 / ALT"),
            ("ast", "天门冬氨酸氨基转移酶 / AST"),
            ("ast_alt", "谷草/谷丙 / AST/ALT"),
            ("tp", "总蛋白 / TP"),
            ("alb", "白蛋白 / ALB"),
            ("glob", "球蛋白 / GLOB / GLB"),
            ("ag_ratio", "白球比 / A/G"),
            ("ggt", "γ-谷氨酰基转移酶 / GGT"),
            ("alp", "碱性磷酸酶 / ALP"),
        ],
    },
    "kidney_function": {
        "label": "肾功能",
        "items": [],
    },
    "ecg": {
        "label": "常规心电图",
        "items": [
            ("routine_ecg", "常规心电图 / ECG"),
        ],
    },
    "imaging": {
        "label": "影像检查",
        "items": [
            ("chest_dr_pa", "DR胸部正位 / 胸部DR"),
        ],
    },
}
SECTION_ITEM_KEYS: dict[str, list[str]] = {
    section_key: [item_key for item_key, _ in section_definition["items"]]
    for section_key, section_definition in SECTION_ITEM_DEFINITIONS.items()
}


class MedicalReportConnectorError(Exception):
    def __init__(self, code: str, message: str, http_status: int = 400) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.http_status = http_status


@dataclass(slots=True)
class MedicalReportConnectorAdapter:
    provider: str
    model_id: str
    api_key: str
    timeout_seconds: int = 30

    def verify_connection(self) -> None:
        self._validate_credentials()
        if self.provider.strip().lower() == "volcengine":
            self._chat_completion(
                messages=[{"role": "user", "content": "Reply with pong."}],
                max_tokens=16,
                temperature=0,
                response_json_object=False,
            )
            return

        self._chat_completion(
            messages=[{"role": "user", "content": "Reply with pong."}],
            max_tokens=4,
            temperature=0,
            response_json_object=False,
        )

    def parse_report(
        self,
        record_number: str,
        report_date: str,
        institution: str,
        file_name: str,
        file_bytes: bytes,
    ) -> dict[str, Any]:
        self._validate_credentials()
        self._validate_pdf(file_name=file_name, file_bytes=file_bytes)

        if self.provider.strip().lower() == "volcengine":
            parsed_json = self._parse_report_with_volcengine_responses(
                record_number=record_number,
                report_date=report_date,
                institution=institution,
                file_name=file_name,
                file_bytes=file_bytes,
            )
        else:
            parsed_json = self._parse_report_with_chat_completion(
                record_number=record_number,
                report_date=report_date,
                institution=institution,
                file_name=file_name,
            )

        return _build_structured_result(report_date=report_date, parsed_model_output=parsed_json)

    def _parse_report_with_chat_completion(
        self,
        *,
        record_number: str,
        report_date: str,
        institution: str,
        file_name: str,
    ) -> dict[str, Any]:
        model_output = self._chat_completion(
            messages=[
                {
                    "role": "system",
                    "content": (
                        "You are a medical report parser. Return JSON only. "
                        "Use keys: form {examiner, examDate} and sections "
                        "[{sectionKey, items[{itemKey,result,referenceValue,unit,abnormalFlag}]}]. "
                        "Do not translate values. Do not infer or fabricate missing fields. "
                        "If a field is not explicitly available, leave it empty."
                    ),
                },
                {
                    "role": "user",
                    "content": (
                        f"recordNumber={record_number}\n"
                        f"reportDate={report_date}\n"
                        f"institution={institution}\n"
                        f"fileName={file_name}\n"
                        "Schema mapping:\n"
                        f"{_build_section_mapping_prompt()}\n"
                        "The original PDF content is not directly available in this request. "
                        "Return only fields you can determine explicitly."
                    ),
                },
            ],
            max_tokens=2000,
            temperature=0,
            response_json_object=True,
        )
        return _extract_json_object(model_output)

    def _parse_report_with_volcengine_responses(
        self,
        *,
        record_number: str,
        report_date: str,
        institution: str,
        file_name: str,
        file_bytes: bytes,
    ) -> dict[str, Any]:
        if len(file_bytes) > VOLCENGINE_FILE_UPLOAD_LIMIT_BYTES:
            raise MedicalReportConnectorError(
                "INVALID_FILE_SIZE",
                "Medical report PDF must be 512 MB or smaller.",
            )

        file_id = self._upload_volcengine_file_and_wait(
            file_name=file_name,
            file_bytes=file_bytes,
        )
        prompt = _build_volcengine_extraction_prompt(
            record_number=record_number,
            report_date=report_date,
            institution=institution,
            file_name=file_name,
        )
        model_output = self._responses_completion(
            input_content=[
                {
                    "type": "input_file",
                    "file_id": file_id,
                },
                {
                    "type": "input_text",
                    "text": prompt,
                },
            ],
            max_output_tokens=6000,
            text_format={
                "type": "json_schema",
                "name": "medical_report_extraction",
                "schema": _build_volcengine_report_json_schema(),
                "strict": True,
            },
        )
        return _extract_json_object(model_output)

    def _upload_volcengine_file_and_wait(self, *, file_name: str, file_bytes: bytes) -> str:
        provider = self.provider.strip().lower()
        endpoint = PROVIDER_FILES_URLS[provider]

        try:
            response = requests.post(
                endpoint,
                headers={
                    "Authorization": f"Bearer {self.api_key.strip()}",
                },
                data={"purpose": "user_data"},
                files={
                    "file": (file_name, file_bytes, "application/pdf"),
                },
                timeout=max(self.timeout_seconds, 180),
            )
        except requests.RequestException as error:
            raise MedicalReportConnectorError(
                "MODEL_CONNECTION_ERROR",
                "Unable to upload the medical report to model provider right now.",
                http_status=502,
            ) from error

        if response.status_code in {401, 403}:
            raise MedicalReportConnectorError(
                "MODEL_AUTH_FAILED",
                "Provider authentication failed.",
            )

        if response.status_code >= 400:
            provider_message = _extract_provider_error_message(response)
            raise MedicalReportConnectorError(
                "REPORT_PARSE_FAILED",
                provider_message or "Unable to upload medical report content.",
                http_status=502,
            )

        payload_json = response.json()
        file_id = str(payload_json.get("id", "")).strip()
        if not file_id:
            raise MedicalReportConnectorError(
                "REPORT_PARSE_FAILED",
                "Provider returned unexpected file upload response.",
                http_status=502,
            )

        deadline = time.monotonic() + VOLCENGINE_FILE_PROCESSING_TIMEOUT_SECONDS
        status = str(payload_json.get("status", "")).strip().lower()
        while status in {"", "uploaded", "processing"}:
            if time.monotonic() >= deadline:
                raise MedicalReportConnectorError(
                    "REPORT_PARSE_FAILED",
                    "Medical report preprocessing timed out at model provider.",
                    http_status=502,
                )

            time.sleep(VOLCENGINE_FILE_POLL_INTERVAL_SECONDS)
            file_meta = self._retrieve_volcengine_file(file_id)
            status = str(file_meta.get("status", "")).strip().lower()
            if status in {"error", "failed", "cancelled"}:
                raise MedicalReportConnectorError(
                    "REPORT_PARSE_FAILED",
                    str(file_meta.get("status_details") or file_meta.get("message") or "Medical report preprocessing failed."),
                    http_status=502,
                )

        return file_id

    def _retrieve_volcengine_file(self, file_id: str) -> dict[str, Any]:
        provider = self.provider.strip().lower()
        endpoint = f"{PROVIDER_FILES_URLS[provider]}/{file_id}"

        try:
            response = requests.get(
                endpoint,
                headers={
                    "Authorization": f"Bearer {self.api_key.strip()}",
                },
                timeout=max(self.timeout_seconds, 60),
            )
        except requests.RequestException as error:
            raise MedicalReportConnectorError(
                "MODEL_CONNECTION_ERROR",
                "Unable to retrieve medical report processing status from model provider.",
                http_status=502,
            ) from error

        if response.status_code in {401, 403}:
            raise MedicalReportConnectorError(
                "MODEL_AUTH_FAILED",
                "Provider authentication failed.",
            )

        if response.status_code >= 400:
            provider_message = _extract_provider_error_message(response)
            raise MedicalReportConnectorError(
                "REPORT_PARSE_FAILED",
                provider_message or "Unable to retrieve medical report processing status.",
                http_status=502,
            )

        payload_json = response.json()
        return payload_json if isinstance(payload_json, dict) else {}

    def _retrieve_volcengine_response(self, response_id: str) -> dict[str, Any]:
        provider = self.provider.strip().lower()
        endpoint = f"{PROVIDER_RESPONSES_URLS[provider]}/{response_id}"

        try:
            response = requests.get(
                endpoint,
                headers={
                    "Authorization": f"Bearer {self.api_key.strip()}",
                },
                timeout=max(self.timeout_seconds, 60),
            )
        except requests.RequestException as error:
            raise MedicalReportConnectorError(
                "MODEL_CONNECTION_ERROR",
                "Unable to retrieve medical report parsing result from model provider.",
                http_status=502,
            ) from error

        if response.status_code in {401, 403}:
            raise MedicalReportConnectorError(
                "MODEL_AUTH_FAILED",
                "Provider authentication failed.",
            )

        if response.status_code >= 400:
            provider_message = _extract_provider_error_message(response)
            raise MedicalReportConnectorError(
                "REPORT_PARSE_FAILED",
                provider_message or "Unable to retrieve medical report parsing result.",
                http_status=502,
            )

        payload_json = response.json()
        return payload_json if isinstance(payload_json, dict) else {}

    def _validate_credentials(self) -> None:
        provider = self.provider.strip().lower()
        model_id = self.model_id.strip()
        api_key = self.api_key.strip()

        if provider not in SUPPORTED_PROVIDERS:
            raise MedicalReportConnectorError("CONNECTOR_VALIDATION_ERROR", "Provider must be DeepSeek or Volcengine.")
        if not model_id:
            raise MedicalReportConnectorError("CONNECTOR_VALIDATION_ERROR", "Model ID is required.")
        if len(api_key) < 8:
            raise MedicalReportConnectorError("CONNECTOR_VALIDATION_ERROR", "API Key is required.")

    def _validate_pdf(self, file_name: str, file_bytes: bytes) -> None:
        normalized_name = file_name.strip().lower()
        if not normalized_name.endswith(".pdf"):
            raise MedicalReportConnectorError("INVALID_FILE_TYPE", "Only PDF files are supported.")
        if len(file_bytes) < len(PDF_MAGIC) or not file_bytes.startswith(PDF_MAGIC):
            raise MedicalReportConnectorError("INVALID_FILE_TYPE", "Only PDF files are supported.")

    def _chat_completion(
        self,
        *,
        messages: list[dict[str, str]],
        max_tokens: int,
        temperature: float,
        response_json_object: bool,
    ) -> str:
        provider = self.provider.strip().lower()
        endpoint = PROVIDER_CHAT_COMPLETION_URLS[provider]
        payload = {
            "model": self.model_id.strip(),
            "messages": messages,
            "max_tokens": max_tokens,
            "temperature": temperature,
            "stream": False,
        }
        if response_json_object:
            payload["response_format"] = {"type": "json_object"}

        try:
            response = requests.post(
                endpoint,
                headers={
                    "Authorization": f"Bearer {self.api_key.strip()}",
                    "Content-Type": "application/json",
                },
                json=payload,
                timeout=self.timeout_seconds,
            )
        except requests.RequestException as error:
            raise MedicalReportConnectorError(
                "MODEL_CONNECTION_ERROR",
                "Unable to reach model provider right now.",
                http_status=502,
            ) from error

        if response.status_code in {401, 403}:
            raise MedicalReportConnectorError(
                "MODEL_AUTH_FAILED",
                "Provider authentication failed.",
            )

        if response.status_code >= 400:
            provider_message = _extract_provider_error_message(response)
            raise MedicalReportConnectorError(
                "REPORT_PARSE_FAILED",
                provider_message or "Unable to parse report content.",
                http_status=502,
            )

        payload_json = response.json()
        try:
            content = payload_json["choices"][0]["message"]["content"]
        except Exception as error:  # pragma: no cover - defensive branch
            raise MedicalReportConnectorError("REPORT_PARSE_FAILED", "Provider returned unexpected response format.", 502) from error

        return str(content or "")

    def _responses_completion(
        self,
        *,
        input_content: list[dict[str, Any]],
        max_output_tokens: int,
        text_format: dict[str, Any] | None = None,
    ) -> str:
        provider = self.provider.strip().lower()
        endpoint = PROVIDER_RESPONSES_URLS[provider]
        payload = {
            "model": self.model_id.strip(),
            "input": [
                {
                    "role": "user",
                    "content": input_content,
                }
            ],
            "max_output_tokens": max_output_tokens,
        }
        if text_format:
            payload["text"] = {"format": text_format}

        try:
            response = requests.post(
                endpoint,
                headers={
                    "Authorization": f"Bearer {self.api_key.strip()}",
                    "Content-Type": "application/json",
                },
                json=payload,
                timeout=max(self.timeout_seconds, 180),
            )
        except requests.RequestException as error:
            raise MedicalReportConnectorError(
                "MODEL_CONNECTION_ERROR",
                "Unable to reach model provider right now.",
                http_status=502,
            ) from error

        if response.status_code in {401, 403}:
            raise MedicalReportConnectorError(
                "MODEL_AUTH_FAILED",
                "Provider authentication failed.",
            )

        if response.status_code >= 400:
            provider_message = _extract_provider_error_message(response)
            raise MedicalReportConnectorError(
                "REPORT_PARSE_FAILED",
                provider_message or "Unable to parse report content.",
                http_status=502,
            )

        payload_json = response.json()
        content = _extract_responses_output_text(payload_json)
        if not content:
            content = self._wait_for_volcengine_response_output(payload_json)

        if not content:
            logger.warning(
                "volcengine responses returned unexpected payload shape: %s",
                _summarize_payload_for_log(payload_json),
            )
            raise MedicalReportConnectorError(
                "REPORT_PARSE_FAILED",
                "Provider returned unexpected response format.",
                http_status=502,
            )

        return content

    def _wait_for_volcengine_response_output(self, payload_json: dict[str, Any]) -> str:
        response_id = str(payload_json.get("id", "")).strip()
        if not response_id:
            return ""

        status = str(payload_json.get("status", "")).strip().lower()
        if status in {"failed", "error", "cancelled"}:
            error_message = _extract_error_message_from_payload(payload_json)
            if error_message:
                raise MedicalReportConnectorError(
                    "REPORT_PARSE_FAILED",
                    error_message,
                    http_status=502,
                )
            return ""

        latest_payload = payload_json
        latest_content = _extract_responses_output_text(latest_payload)
        deadline = time.monotonic() + VOLCENGINE_RESPONSE_PROCESSING_TIMEOUT_SECONDS

        while not latest_content and time.monotonic() < deadline:
            if status in {"completed"}:
                break

            time.sleep(VOLCENGINE_RESPONSE_POLL_INTERVAL_SECONDS)
            latest_payload = self._retrieve_volcengine_response(response_id)
            status = str(latest_payload.get("status", "")).strip().lower()
            if status in {"failed", "error", "cancelled"}:
                error_message = _extract_error_message_from_payload(latest_payload)
                raise MedicalReportConnectorError(
                    "REPORT_PARSE_FAILED",
                    error_message or "Model provider failed to complete document parsing.",
                    http_status=502,
                )
            latest_content = _extract_responses_output_text(latest_payload)

        if not latest_content and time.monotonic() >= deadline:
            raise MedicalReportConnectorError(
                "REPORT_PARSE_FAILED",
                "Medical report parsing timed out at model provider.",
                http_status=502,
            )

        if not latest_content:
            logger.warning(
                "volcengine response completed without output text: %s",
                _summarize_payload_for_log(latest_payload),
            )

        return latest_content


def decode_pdf_base64(file_base64: str) -> bytes:
    value = file_base64.strip()
    if not value:
        raise MedicalReportConnectorError("VALIDATION_ERROR", "Medical report file is required.")

    try:
        return base64.b64decode(value, validate=True)
    except Exception as error:
        raise MedicalReportConnectorError("VALIDATION_ERROR", "Invalid medical report file payload.") from error


def _extract_provider_error_message(response: requests.Response) -> str:
    try:
        payload = response.json()
    except Exception:
        return response.text.strip()

    error = payload.get("error")
    if isinstance(error, dict):
        message = error.get("message")
        if isinstance(message, str):
            return message.strip()
    if isinstance(error, str):
        return error.strip()

    message = payload.get("message")
    if isinstance(message, str):
        return message.strip()
    return ""


def _extract_json_object(content: str) -> dict[str, Any]:
    text = content.strip()
    if not text:
        return {}

    try:
        decoded = json.loads(text)
        return decoded if isinstance(decoded, dict) else {}
    except json.JSONDecodeError:
        pass

    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        return {}

    try:
        decoded = json.loads(match.group(0))
        return decoded if isinstance(decoded, dict) else {}
    except json.JSONDecodeError:
        return {}


def _extract_responses_output_text(payload_json: dict[str, Any]) -> str:
    candidate_roots: list[dict[str, Any]] = [payload_json]
    for key in ("response", "data"):
        nested = payload_json.get(key)
        if isinstance(nested, dict):
            candidate_roots.append(nested)

    for root in candidate_roots:
        direct_output_text = root.get("output_text")
        if isinstance(direct_output_text, str) and direct_output_text.strip():
            return direct_output_text.strip()
        if isinstance(direct_output_text, list):
            joined_output_text = "\n".join(str(part) for part in direct_output_text if str(part).strip()).strip()
            if joined_output_text:
                return joined_output_text

        chunks = _extract_output_chunks(root.get("output"))
        if chunks:
            return "\n".join(chunks).strip()

        chunks = _extract_output_chunks(root.get("content"))
        if chunks:
            return "\n".join(chunks).strip()

    return ""


def _extract_output_chunks(value: Any) -> list[str]:
    chunks: list[str] = []
    if isinstance(value, list):
        for item in value:
            chunks.extend(_extract_output_chunks(item))
        return chunks

    if not isinstance(value, dict):
        return chunks

    text = value.get("text")
    if isinstance(text, str) and text.strip():
        chunks.append(text.strip())

    output_text = value.get("output_text")
    if isinstance(output_text, str) and output_text.strip():
        chunks.append(output_text.strip())

    for key in ("content", "output"):
        nested = value.get(key)
        if isinstance(nested, (list, dict)):
            chunks.extend(_extract_output_chunks(nested))

    return chunks


def _extract_error_message_from_payload(payload_json: dict[str, Any]) -> str:
    error = payload_json.get("error")
    if isinstance(error, dict):
        message = error.get("message")
        if isinstance(message, str) and message.strip():
            return message.strip()
    if isinstance(error, str) and error.strip():
        return error.strip()

    incomplete_details = payload_json.get("incomplete_details")
    if isinstance(incomplete_details, dict):
        reason = incomplete_details.get("reason")
        if isinstance(reason, str) and reason.strip():
            return reason.strip()

    message = payload_json.get("message")
    if isinstance(message, str) and message.strip():
        return message.strip()
    return ""


def _summarize_payload_for_log(payload_json: dict[str, Any]) -> str:
    try:
        return json.dumps(payload_json, ensure_ascii=False)[:4000]
    except Exception:
        return repr(payload_json)[:4000]


def _build_section_mapping_prompt() -> str:
    lines: list[str] = []
    for section_key, section_definition in SECTION_ITEM_DEFINITIONS.items():
        lines.append(f"{section_key} ({section_definition['label']}):")
        for item_key, item_label in section_definition["items"]:
            lines.append(f"- {item_key} = {item_label}")
    return "\n".join(lines)


def _build_volcengine_extraction_prompt(
    *,
    record_number: str,
    report_date: str,
    institution: str,
    file_name: str,
) -> str:
    return (
        "Read the attached medical examination PDF and return JSON only. "
        "Do not output markdown, explanations, or code fences.\n\n"
        "Rules:\n"
        "1. Copy extracted values exactly as they appear in the PDF.\n"
        "2. Keep the original language, wording, punctuation, symbols, ranges, units, abnormal markers, and abbreviations.\n"
        "3. Do not translate Chinese to English or English to Chinese.\n"
        "4. Do not normalize, summarize, infer, calculate, or fabricate missing values.\n"
        "5. Only return items that are explicitly present in the PDF.\n"
        "6. For each matched item, copy result, referenceValue, unit, and abnormalFlag from the same row/cell when available.\n"
        "7. If examiner is not explicitly shown, return an empty string for form.examiner.\n"
        "8. If examDate is not explicitly shown, use the provided reportDate string exactly.\n"
        "9. The response schema is enforced separately. Under sections, use sectionKey objects and itemKey objects exactly as listed below.\n"
        "10. Omit missing item objects entirely instead of returning guessed values or placeholders.\n\n"
        "Return shape:\n"
        '{"form":{"examiner":"","examDate":""},"sections":{"general":{"height":{"result":"","referenceValue":"","unit":"","abnormalFlag":""}}}}\n\n'
        "Provided metadata:\n"
        f"- recordNumber: {record_number}\n"
        f"- reportDate: {report_date}\n"
        f"- institution: {institution}\n"
        f"- fileName: {file_name}\n\n"
        "Schema mapping:\n"
        f"{_build_section_mapping_prompt()}"
    )


def _build_structured_result(report_date: str, parsed_model_output: dict[str, Any]) -> dict[str, Any]:
    form = parsed_model_output.get("form")
    form_examiner = ""
    form_exam_date = report_date
    if isinstance(form, dict):
        examiner = form.get("examiner")
        exam_date = form.get("examDate")
        if isinstance(examiner, str) and examiner.strip():
            form_examiner = examiner.strip()
        if isinstance(exam_date, str) and exam_date.strip():
            form_exam_date = exam_date.strip()

    parsed_sections = parsed_model_output.get("sections")
    model_values: dict[str, dict[str, dict[str, str]]] = {}
    if isinstance(parsed_sections, dict):
        for section_key, section_items in parsed_sections.items():
            normalized_section_key = str(section_key).strip()
            if normalized_section_key not in SECTION_ITEM_KEYS or not isinstance(section_items, dict):
                continue
            model_values.setdefault(normalized_section_key, {})
            for item_key, item_values in section_items.items():
                normalized_item_key = str(item_key).strip()
                if normalized_item_key not in SECTION_ITEM_KEYS[normalized_section_key] or not isinstance(item_values, dict):
                    continue
                model_values[normalized_section_key][normalized_item_key] = {
                    "result": str(item_values.get("result", "")).strip(),
                    "referenceValue": str(item_values.get("referenceValue", "")).strip(),
                    "unit": str(item_values.get("unit", "")).strip(),
                    "abnormalFlag": str(item_values.get("abnormalFlag", "")).strip(),
                }
    elif isinstance(parsed_sections, list):
        for section in parsed_sections:
            if not isinstance(section, dict):
                continue
            section_key = str(section.get("sectionKey", "")).strip()
            if section_key not in SECTION_ITEM_KEYS:
                continue
            model_values.setdefault(section_key, {})
            items = section.get("items")
            if not isinstance(items, list):
                continue
            for item in items:
                if not isinstance(item, dict):
                    continue
                item_key = str(item.get("itemKey", "")).strip()
                if item_key not in SECTION_ITEM_KEYS[section_key]:
                    continue
                model_values[section_key][item_key] = {
                    "result": str(item.get("result", "")).strip(),
                    "referenceValue": str(item.get("referenceValue", "")).strip(),
                    "unit": str(item.get("unit", "")).strip(),
                    "abnormalFlag": str(item.get("abnormalFlag", "")).strip(),
                }

    sections: list[dict[str, Any]] = []
    for section_key, item_keys in SECTION_ITEM_KEYS.items():
        items: list[dict[str, str]] = []
        for item_key in item_keys:
            values = model_values.get(section_key, {}).get(item_key, {})
            items.append(
                {
                    "itemKey": item_key,
                    "result": values.get("result", ""),
                    "referenceValue": values.get("referenceValue", ""),
                    "unit": values.get("unit", ""),
                    "abnormalFlag": values.get("abnormalFlag", ""),
                }
            )
        sections.append({"sectionKey": section_key, "items": items})

    return {
        "form": {
            "examiner": form_examiner,
            "examDate": form_exam_date,
        },
        "sections": sections,
    }


def _build_volcengine_report_json_schema() -> dict[str, Any]:
    value_schema = {
        "type": "object",
        "properties": {
            "result": {"type": "string"},
            "referenceValue": {"type": "string"},
            "unit": {"type": "string"},
            "abnormalFlag": {"type": "string"},
        },
        "required": ["result", "referenceValue", "unit", "abnormalFlag"],
        "additionalProperties": False,
    }

    section_properties: dict[str, Any] = {}
    for section_key, item_keys in SECTION_ITEM_KEYS.items():
        item_properties = {item_key: value_schema for item_key in item_keys}
        section_properties[section_key] = {
            "type": "object",
            "properties": item_properties,
            "additionalProperties": False,
        }

    return {
        "type": "object",
        "properties": {
            "form": {
                "type": "object",
                "properties": {
                    "examiner": {"type": "string"},
                    "examDate": {"type": "string"},
                },
                "required": ["examiner", "examDate"],
                "additionalProperties": False,
            },
            "sections": {
                "type": "object",
                "properties": section_properties,
                "required": [],
                "additionalProperties": False,
            },
        },
        "required": ["form", "sections"],
        "additionalProperties": False,
    }
