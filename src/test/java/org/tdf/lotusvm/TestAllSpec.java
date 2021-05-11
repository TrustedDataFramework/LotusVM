package org.tdf.lotusvm;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class TestAllSpec {
    public static ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(JsonParser.Feature.ALLOW_COMMENTS);

    String[] dirs = {"testdata/spec", "testdata"};

}
