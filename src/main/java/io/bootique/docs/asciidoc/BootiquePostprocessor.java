/*
 * Licensed to ObjectStyle LLC under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ObjectStyle LLC licenses
 * this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.bootique.docs.asciidoc;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.asciidoctor.Options;
import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.Postprocessor;
import org.jsoup.Jsoup;

/**
 *
 */
public class BootiquePostprocessor extends Postprocessor {

    private static final String FRONT_MATTER = "front-matter";
    private static final String EMPTY_FRONT_MATTER = "---\n---\n\n";

    private static final Map<String, String> ICONS_REPLACEMENT = new HashMap<>();
    static {
        ICONS_REPLACEMENT.put("icon-tip", "fa-lightbulb-o");
        ICONS_REPLACEMENT.put("icon-note", "fa-info-circle");
        ICONS_REPLACEMENT.put("icon-important", "fa-exclamation-circle");
        ICONS_REPLACEMENT.put("icon-warning", "fa-exclamation-triangle");
        ICONS_REPLACEMENT.put("icon-caution", "fa-exclamation-triangle");
    }

    @SuppressWarnings("unused")
    public BootiquePostprocessor() {
        super();
    }

    @SuppressWarnings("unused")
    public BootiquePostprocessor(Map<String, Object> config) {
        super(config);
    }

    @Override
    public String process(Document document, String content) {
        content = extractTableOfContents(document, content);
        content = fixupDom(document, content);
        content = processHeader(document, content);
        content = processFooter(document, content);
        return content;
    }

    private String fixupDom(Document document, String output) {
        org.jsoup.nodes.Document jsoupDoc = Jsoup.parseBodyFragment(output);

        for(Map.Entry<String, String> replacements: ICONS_REPLACEMENT.entrySet()) {
            jsoupDoc.select("." + replacements.getKey())
                    .removeClass(replacements.getKey())
                    .addClass(replacements.getValue())
                    .addClass("fa-2x");
        }

        jsoupDoc.select("code").forEach(el -> {
            String codeClass = el.attr("data-lang");
            if(!codeClass.isEmpty()) {
                el.addClass(codeClass);
            }
        });

        jsoupDoc.select("div#preamble").remove();

        return jsoupDoc.body().html();
    }

    private String extractTableOfContents(Document document, String output) {
        int start = output.indexOf("<div id=\"toc\" class=\"toc\">");
        if(start == -1) {
            // no toc found, exit
            return output;
        }

        String tocEndString = "</ul>\n</div>";
        int end = output.indexOf(tocEndString, start);
        if(end == -1) {
            // bad, no end..
            return output;
        }

        end += tocEndString.length() + 1;

        // normalize ToC, also modify layout here as needed
        org.jsoup.nodes.Document tocDoc = Jsoup.parseBodyFragment(output.substring(start, end));
        tocDoc.select("ul").addClass("nav");
        tocDoc.select("a").addClass("nav-link");
        String toc = tocDoc.body().html();

        Object destDir = document.getOptions().get(Options.DESTINATION_DIR);
        Object docname = ((Map)document.getOptions().get(Options.ATTRIBUTES)).get("docname");

        Path path = FileSystems.getDefault().getPath((String) destDir, docname + ".toc.html");
        StandardOpenOption[] options = {
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        };
        try(BufferedWriter br = Files.newBufferedWriter(path, options)) {
            br.write(toc, 0, toc.length());
            br.flush();
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }

        if(start == 0) {
            return output.substring(end);
        }

        return output.substring(0, start) + output.substring(end);
    }

    private String processHeader(Document document, String output) {
        String headerFile = (String) document.getAttribute("bq-header", "");
        if(headerFile.isEmpty()) {
            return output;
        }

        String header;
        // inject empty front matter
        if(FRONT_MATTER.equals(headerFile.trim())) {
            header = EMPTY_FRONT_MATTER;
        } else {
            // treat as a file
            header = document.readAsset(headerFile, Collections.emptyMap());
        }

        return header + output;
    }

    private String processFooter(Document document, String output) {
        String footerFile = (String) document.getAttribute("bq-footer", "");
        if(footerFile.isEmpty()) {
            return output;
        }

        String footer = document.readAsset(footerFile, Collections.emptyMap());
        return output + footer;
    }
}
