package com.savanna.backend.service;

public interface TeamPhotoStorage {
    void put(String key, byte[] bytes, String contentType);
    String readUrl(String key);
}
