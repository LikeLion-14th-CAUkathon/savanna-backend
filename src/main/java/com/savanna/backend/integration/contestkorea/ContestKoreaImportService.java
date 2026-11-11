package com.savanna.backend.integration.contestkorea;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContestKoreaImportService {
    private final ContestKoreaClient client;
    private final ContestKoreaWriter writer;

    // 네트워크 호출은 저장 트랜잭션 밖에서 수행합니다. 자동 실행하지 않습니다.
    public ContestKoreaWriter.ImportResult sync(ContestKoreaCategory category) {
        return writer.save(category, client.fetch(category));
    }
}
