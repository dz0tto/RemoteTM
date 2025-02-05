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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.maxprograms.remotetm.Constants;
import com.maxprograms.remotetm.DbManager;
import com.maxprograms.remotetm.RemoteTM;
import com.maxprograms.remotetm.models.User;
import com.maxprograms.remotetm.utils.Utils;

import org.json.JSONObject;

public class EmailServerServlet extends HttpServlet {

    private static final long serialVersionUID = -735904418329940685L;

    private static Logger logger = System.getLogger(EmailServerServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            if (!Utils.isSafe(request, response)) {
                return;
            }
            JSONObject result = new JSONObject();
            String session = request.getHeader("Session");
            if (AuthorizeServlet.sessionActive(session)) {
                DbManager manager = DbManager.getInstance();
                User who = manager.getUser(AuthorizeServlet.getUser(session));
                if (who != null && who.isActive() && Constants.SYSTEM_ADMINISTRATOR.equals(who.getRole())) {
                    File emailServer = new File(RemoteTM.getWorkFolder(), "mailserver.json");
                    if (!emailServer.exists()) {
                        JSONObject json = new JSONObject();
                        json.put("server", "");
                        json.put("port", "");
                        json.put("user", "");
                        json.put("password", "");
                        json.put("from", "");
                        json.put("instance", "");
                        json.put("authenticate", false);
                        json.put("tls", false);
                        try (FileOutputStream out = new FileOutputStream(emailServer)) {
                            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    try (FileInputStream input = new FileInputStream(emailServer)) {
                        result = Utils.readJSON(input);
                    }
                    result.put(Constants.STATUS, Constants.OK);
                    Utils.writeResponse(result, response, 200);
                    return;
                }
            }
            Utils.denyAccess(response);
        } catch (IOException | SQLException | NoSuchAlgorithmException e) {
            logger.log(Level.ERROR, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            if (!Utils.isSafe(request, response)) {
                return;
            }
            JSONObject result = new JSONObject();
            String session = request.getHeader("Session");
            if (AuthorizeServlet.sessionActive(session)) {
                DbManager manager = DbManager.getInstance();
                User who = manager.getUser(AuthorizeServlet.getUser(session));
                if (who != null && who.isActive() && Constants.SYSTEM_ADMINISTRATOR.equals(who.getRole())) {
                    JSONObject json = Utils.readJSON(request.getInputStream());
                    File emailServer = new File(RemoteTM.getWorkFolder(), "mailserver.json");
                    try (FileOutputStream out = new FileOutputStream(emailServer)) {
                        out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                    result.put(Constants.STATUS, Constants.OK);
                    Utils.writeResponse(result, response, 200);
                    return;
                }
            }
            Utils.denyAccess(response);
        } catch (IOException | SQLException | NoSuchAlgorithmException e) {
            logger.log(Level.ERROR, e);
        }
    }
}
