package com.ionhex975.vulkanpostfx.client.runtime.zip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class ZipPostEffectNamespaceRewriter {
    private ZipPostEffectNamespaceRewriter() {
    }

    public static String rewrite(String rawJson, String originalNamespace, String runtimeNamespace) {
        JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();

        rewriteTargets(root, originalNamespace, runtimeNamespace);
        rewritePasses(root, originalNamespace, runtimeNamespace);

        return root.toString();
    }

    private static void rewriteTargets(JsonObject root, String originalNamespace, String runtimeNamespace) {
        if (!root.has("targets") || !root.get("targets").isJsonObject()) {
            return;
        }

        JsonObject oldTargets = root.getAsJsonObject("targets");
        JsonObject newTargets = new JsonObject();

        for (String key : oldTargets.keySet()) {
            String rewrittenKey = rewriteNamespacedString(key, originalNamespace, runtimeNamespace);
            newTargets.add(rewrittenKey, oldTargets.get(key));
        }

        root.add("targets", newTargets);
    }

    private static void rewritePasses(JsonObject root, String originalNamespace, String runtimeNamespace) {
        if (!root.has("passes") || !root.get("passes").isJsonArray()) {
            return;
        }

        JsonArray passes = root.getAsJsonArray("passes");
        for (JsonElement passElement : passes) {
            if (!passElement.isJsonObject()) {
                continue;
            }

            JsonObject pass = passElement.getAsJsonObject();

            rewriteStringField(pass, "vertex_shader", originalNamespace, runtimeNamespace);
            rewriteStringField(pass, "fragment_shader", originalNamespace, runtimeNamespace);
            rewriteStringField(pass, "output", originalNamespace, runtimeNamespace);

            if (pass.has("inputs") && pass.get("inputs").isJsonArray()) {
                JsonArray inputs = pass.getAsJsonArray("inputs");
                for (JsonElement inputElement : inputs) {
                    if (!inputElement.isJsonObject()) {
                        continue;
                    }

                    JsonObject input = inputElement.getAsJsonObject();
                    rewriteStringField(input, "target", originalNamespace, runtimeNamespace);
                }
            }
        }
    }

    private static void rewriteStringField(
            JsonObject object,
            String fieldName,
            String originalNamespace,
            String runtimeNamespace
    ) {
        if (!object.has(fieldName) || !object.get(fieldName).isJsonPrimitive()) {
            return;
        }

        String value = object.get(fieldName).getAsString();
        object.addProperty(fieldName, rewriteNamespacedString(value, originalNamespace, runtimeNamespace));
    }

    private static String rewriteNamespacedString(
            String value,
            String originalNamespace,
            String runtimeNamespace
    ) {
        String prefix = originalNamespace + ":";
        if (value.startsWith(prefix)) {
            return runtimeNamespace + ":" + value.substring(prefix.length());
        }
        return value;
    }
}