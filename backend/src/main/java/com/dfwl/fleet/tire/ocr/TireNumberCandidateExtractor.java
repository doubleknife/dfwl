package com.dfwl.fleet.tire.ocr;

import com.dfwl.fleet.tire.repository.TireRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TireNumberCandidateExtractor {

    private static final Pattern ALNUM = Pattern.compile("[A-Z0-9]{5,}");

    private final TireRepository tireRepository;

    public TireNumberCandidateExtractor(TireRepository tireRepository) {
        this.tireRepository = tireRepository;
    }

    public List<Candidate> extract(List<OcrProviderResult.DetectedText> detections) {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (OcrProviderResult.DetectedText detection : detections == null ? List.<OcrProviderResult.DetectedText>of() : detections) {
            String source = detection.text() == null ? "" : detection.text().trim();
            String normalized = source.toUpperCase(Locale.ROOT).replaceAll("[\\s\\-_/\\\\.:：]+", "");
            Matcher matcher = ALNUM.matcher(normalized);
            while (matcher.find()) {
                String candidate = matcher.group();
                candidates.merge(candidate,
                        new Candidate(candidate, detection.confidence(), source, tireRepository.tireNoExists(candidate)),
                        this::merge);
            }
        }
        return candidates.values().stream()
                .sorted(Comparator
                        .comparing(Candidate::inventoryMatched).reversed()
                        .thenComparing(candidate -> candidate.reasonableLongCode() ? 1 : 0, Comparator.reverseOrder())
                        .thenComparing(Candidate::confidenceOrZero, Comparator.reverseOrder())
                        .thenComparing(Candidate::candidate))
                .toList();
    }

    private Candidate merge(Candidate left, Candidate right) {
        double leftConfidence = left.confidenceOrZero();
        double rightConfidence = right.confidenceOrZero();
        return rightConfidence > leftConfidence ? right : left;
    }

    public String joinedText(List<OcrProviderResult.DetectedText> detections) {
        if (detections == null || detections.isEmpty()) {
            return "";
        }
        return detections.stream()
                .map(OcrProviderResult.DetectedText::text)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    public record Candidate(String candidate, Double confidence, String sourceText, boolean inventoryMatched) {
        double confidenceOrZero() {
            return confidence == null ? 0D : confidence;
        }

        boolean reasonableLongCode() {
            return candidate != null && candidate.length() >= 8;
        }
    }
}
