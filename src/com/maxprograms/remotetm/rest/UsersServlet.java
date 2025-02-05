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

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.maxprograms.remotetm.Constants;
import com.maxprograms.remotetm.DbManager;
import com.maxprograms.remotetm.models.EmailServer;
import com.maxprograms.remotetm.models.User;
import com.maxprograms.remotetm.utils.Crypto;
import com.maxprograms.remotetm.utils.SendMail;
import com.maxprograms.remotetm.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

public class UsersServlet extends HttpServlet {

    private static final long serialVersionUID = 1274203013996176701L;
    private static Logger logger = System.getLogger(UsersServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            if (!Utils.isSafe(request, response)) {
                return;
            }
            String id = request.getParameter("id");
            JSONObject result = new JSONObject();
            String session = request.getHeader("Session");
            if (AuthorizeServlet.sessionActive(session)) {
                try {
                    DbManager manager = DbManager.getInstance();
                    User who = manager.getUser(AuthorizeServlet.getUser(session));
                    if (who != null && who.isActive() && Constants.SYSTEM_ADMINISTRATOR.equals(who.getRole())) {
                        if (id != null) {
                            result.put("user", manager.getUser(id).toJSON());
                        } else {
                            JSONArray array = new JSONArray();
                            List<User> users = manager.getUsers();
                            Iterator<User> it = users.iterator();
                            while (it.hasNext()) {
                                array.put(it.next().toJSON());
                            }
                            result.put("users", array);
                        }
                        result.put(Constants.STATUS, Constants.OK);
                        Utils.writeResponse(result, response, 200);
                    }
                } catch (NoSuchAlgorithmException | SQLException e) {
                    logger.log(Level.ERROR, e);
                    result.put(Constants.STATUS, Constants.ERROR);
                    result.put(Constants.REASON, e.getMessage());
                    Utils.writeResponse(result, response, 500);
                }
                return;
            }
            Utils.denyAccess(response);
        } catch (IOException e) {
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
                try {
                    JSONObject body = Utils.readJSON(request.getInputStream());
                    String command = body.getString("command");
                    switch (command) {
                    case "addUser":
                        addUser(session, body);
                        break;
                    case "updateUser":
                        updateUser(session, body);
                        break;
                    case "removeUser":
                        removeUser(session, body);
                        break;
                    case "toggleLock":
                        toggleLock(session, body);
                        break;
                    case "changePassword":
                        changePassword(session, body);
                        break;
                    default:
                        Utils.denyAccess(response);
                        return;
                    }
                    result.put(Constants.STATUS, Constants.OK);
                    Utils.writeResponse(result, response, 200);
                } catch (IOException | NoSuchAlgorithmException | SQLException | MessagingException e) {
                    logger.log(Level.ERROR, e);
                    result.put(Constants.STATUS, Constants.ERROR);
                    result.put(Constants.REASON, e.getMessage());
                    Utils.writeResponse(result, response, 500);
                }
                return;
            }
            Utils.denyAccess(response);
        } catch (IOException e) {
            logger.log(Level.ERROR, e);
        }
    }

    private void updateUser(String session, JSONObject body)
            throws NoSuchAlgorithmException, SQLException, IOException {
        DbManager manager = DbManager.getInstance();
        User who = manager.getUser(AuthorizeServlet.getUser(session));
        if (!Constants.SYSTEM_ADMINISTRATOR.equals(who.getRole())) {
            throw new IOException(Constants.DENIED);
        }
        User user = manager.getUser(body.getString("id"));
        user.setName(body.getString("name"));
        user.setRole(body.getString("role"));
        user.setEmail(body.getString("email"));
        manager.updateUser(user);
    }

    private void toggleLock(String session, JSONObject body)
            throws NoSuchAlgorithmException, IOException, SQLException {
        DbManager manager = DbManager.getInstance();
        User who = manager.getUser(AuthorizeServlet.getUser(session));
        if (!Constants.SYSTEM_ADMINISTRATOR.equals(who.getRole())) {
            throw new IOException(Constants.DENIED);
        }
        User user = manager.getUser(body.getString("id"));
        user.setActive(!user.isActive());
        manager.updateUser(user);
    }

    private void changePassword(String session, JSONObject json)
            throws NoSuchAlgorithmException, IOException, SQLException {
        DbManager manager = DbManager.getInstance();
        User who = manager.getUser(AuthorizeServlet.getUser(session));
        String current = json.getString("current");
        String newPassword = json.getString("newPassword");
        if (who.getPassword().equals(Crypto.sha256(current))) {
            manager.setPassword(who.getId(), newPassword);
            return;
        }
        throw new IOException(Constants.DENIED);
    }

    private void addUser(String session, JSONObject body)
            throws NoSuchAlgorithmException, IOException, SQLException, MessagingException {
        DbManager manager = DbManager.getInstance();
        User who = manager.getUser(AuthorizeServlet.getUser(session));
        if (!Constants.SYSTEM_ADMINISTRATOR.equals(who.getRole())) {
            throw new IOException(Constants.DENIED);
        }
        String password = Utils.generatePassword();
        User user = new User(body.getString("id"), password, body.getString("name"), body.getString("email"),
                body.getString("role"), true, false);
        EmailServer server = Utils.getEmailServer();
        SendMail sender = new SendMail(server);
        manager.addUser(user);

        String text = "\nDear " + user.getName() + ",\n\nA new account has been created for you in RemoteTM."
                + "\n\nPlease login to the server using the credentials provided below.\n\n" + "  RemoteTM Server: "
                + server.getInstanceUrl() + "\n  User Name: " + user.getId() + "\n  Password: " + password
                + " \n\nThanks for using RemoteTM.\n\n";

        String html = "<p>Dear " + user.getName() + ",</p>"
                + "<p>A new account has been created for you in RemoteTM.</p>"
                + "<p>Please login to the server using the credentials provided below.</p>" + "<pre>  RemoteTM Server: "
                + server.getInstanceUrl() + "\n  User Name: " + user.getId() + "\n  Password: " + password + "</pre>"
                + "<p>Thanks for using RemoteTM.</p>";
        try {
            sender.sendMail(new String[] { user.getEmail() }, new String[] {}, new String[] {},
                    "[RemoteTM] New Account", text, html);
        } catch (MessagingException e) {
            manager.rollback();
            throw e;
        }
        manager.commit();
    }

    private void removeUser(String session, JSONObject body)
            throws NoSuchAlgorithmException, IOException, SQLException {
        DbManager manager = DbManager.getInstance();
        User who = manager.getUser(AuthorizeServlet.getUser(session));
        if (!Constants.SYSTEM_ADMINISTRATOR.equals(who.getRole())) {
            throw new IOException(Constants.DENIED);
        }
        manager.removeUser(body.getString("id"));
    }
}
