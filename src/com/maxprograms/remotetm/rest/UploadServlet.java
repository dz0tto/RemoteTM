/*******************************************************************************
 * Copyright (c) 2008-2023 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/

package com.maxprograms.remotetm.rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.maxprograms.remotetm.Constants;
import com.maxprograms.remotetm.RemoteTM;
import com.maxprograms.remotetm.utils.Utils;

import org.json.JSONObject;

public class UploadServlet extends HttpServlet {

    private static final long serialVersionUID = 3856885250117003115L;

    private static Logger logger = System.getLogger(UploadServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            if (!Utils.isSafe(request, response)) {
                return;
            }
            JSONObject result = new JSONObject();
            String session = request.getHeader("Session");
            if (AuthorizeServlet.sessionActive(session)) {
                boolean zipped = request.getHeader("Content-Type").equals("application/zip");
                try {
                    File homeDir = RemoteTM.getWorkFolder();
                    File tempDir = new File(homeDir, "tmp");
                    if (!tempDir.exists()) {
                        Files.createDirectories(tempDir.toPath());
                    }
                    File temp = File.createTempFile("uploaded", zipped ? ".zip" : ".tmx", tempDir);
                    try (FileOutputStream out = new FileOutputStream(temp)) {
                        try (ServletInputStream input = request.getInputStream()) {
                            if (zipped) {
                                byte[] bytes = new byte[4096];
                                int read = -1;
                                while ((read = input.read(bytes)) != -1) {
                                    out.write(bytes, 0, read);
                                }
                            } else {
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
                                    String boundary = "";
                                    String disposition = "";
                                    String contentType = "";
                                    String line = "";
                                    boolean firstWritten = false;
                                    while ((line = reader.readLine()) != null) {
                                        if (boundary.isEmpty()) {
                                            boundary = line;
                                            continue;
                                        }
                                        if (disposition.isEmpty() && line.startsWith("Content-Disposition")) {
                                            disposition = line;
                                            continue;
                                        }
                                        if (contentType.isEmpty() && line.startsWith("Content-Type")) {
                                            contentType = line;
                                            continue;
                                        }
                                        if (line.startsWith(boundary)) {
                                            break;
                                        }
                                        if (firstWritten) {
                                            out.write(("\n").getBytes(StandardCharsets.UTF_8));
                                        }
                                        out.write((line).getBytes(StandardCharsets.UTF_8));
                                        if (!line.isBlank()) {
                                            firstWritten = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (zipped) {
                        File tmx = File.createTempFile("uploaded", ".tmx", temp.getParentFile());
                        try (ZipInputStream in = new ZipInputStream(new FileInputStream(temp))) {
                            ZipEntry entry = null;
                            while ((entry = in.getNextEntry()) != null) {
                                tmx = new File(temp.getParentFile(), entry.getName());
                                try (FileOutputStream output = new FileOutputStream(tmx.getAbsolutePath())) {
                                    byte[] bytes = new byte[4096];
                                    int read;
                                    while ((read = in.read(bytes)) > 0) {
                                        output.write(bytes, 0, read);
                                    }
                                }
                            }
                        }
                        Files.delete(temp.toPath());
                        result.put("file", tmx.getName());
                    } else {
                        result.put("file", temp.getName());
                    }
                    result.put(Constants.STATUS, Constants.OK);
                } catch (Exception e) {
                    result.put(Constants.STATUS, Constants.ERROR);
                    result.put(Constants.REASON, e.getMessage());
                    logger.log(Level.ERROR, "File upload error", e);
                }
                Utils.writeResponse(result, response, 200);
                return;
            }
            Utils.denyAccess(response);
        } catch (IOException e) {
            logger.log(Level.ERROR, e);
        }
    }

}
