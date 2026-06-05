package com.nowini.ai.infra;

/**
 * LLM 응답에서 JSON 본문만 견고하게 추출한다.
 * <p>
 * Gemini 는 URL Context 도구 사용 시 {@code responseMimeType=application/json}(JSON 강제 모드)을
 * 함께 쓸 수 없어, 응답을 {@code ```json ... ```} 코드펜스로 감싸거나 앞뒤에 설명 텍스트를 붙이는
 * 경우가 있다. 이를 정제하지 않고 그대로 파싱하면 백틱/잡텍스트에서 파싱이 깨진다.
 * <p>
 * 동작: (1) 코드펜스 제거 → (2) 첫 {@code &#123;}/{@code [} 부터 대응하는 마지막 {@code &#125;}/{@code ]}
 * 까지 추출. JSON 시작을 못 찾으면 원본을 그대로 돌려 호출자(파서)가 에러를 처리하게 한다.
 */
public final class LlmJson {

    private LlmJson() {
    }

    public static String extract(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();

        // 1) 코드펜스 제거: ```json\n...\n``` 또는 ```\n...\n```
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            int closingFence = s.lastIndexOf("```");
            if (closingFence >= 0) {
                s = s.substring(0, closingFence);
            }
            s = s.trim();
        }

        // 2) 첫 JSON 시작 문자부터 대응하는 마지막 닫힘 문자까지 추출
        int start = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return s; // JSON 시작을 못 찾음 → 원본 반환(파서가 에러 처리)
        }
        char close = s.charAt(start) == '{' ? '}' : ']';
        int end = s.lastIndexOf(close);
        return end > start ? s.substring(start, end + 1) : s.substring(start);
    }
}
