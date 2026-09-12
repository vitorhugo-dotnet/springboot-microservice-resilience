package com.vitorhugo.demoui.client;

public record ApiCallResult(
        String method, String url, Integer status, String body,
        String friendlyMessage, boolean successful) {
}
