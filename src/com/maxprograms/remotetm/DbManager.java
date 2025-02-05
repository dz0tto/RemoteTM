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

package com.maxprograms.remotetm;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.maxprograms.remotetm.models.Permission;
import com.maxprograms.remotetm.models.User;
import com.maxprograms.remotetm.utils.Crypto;
import com.maxprograms.swordfish.models.Memory;

import org.json.JSONArray;
import org.json.JSONObject;

public class DbManager {

    private static Logger logger = System.getLogger(DbManager.class.getName());
    private Connection conn;

    private static DbManager instance;

    public static DbManager getInstance() throws NoSuchAlgorithmException, IOException, SQLException {
        if (instance == null || instance.isClosed()) {
            instance = new DbManager();
        }
        return instance;
    }

    private boolean isClosed() throws SQLException {
        return conn.isClosed();
    }

    private DbManager() throws IOException, SQLException, NoSuchAlgorithmException {
        File database = new File(RemoteTM.getWorkFolder(), "h2data");
        boolean needsLoading = !database.exists();
        if (!database.exists()) {
            database.mkdirs();
        }
        DriverManager.registerDriver(new org.h2.Driver());
        String url = "jdbc:h2:" + database.getAbsolutePath() + "/h2db;DB_CLOSE_ON_EXIT=FALSE";
        conn = DriverManager.getConnection(url);
        conn.setAutoCommit(false);
        if (needsLoading) {
            createTables();
        }
    }

    private void createTables() throws SQLException, NoSuchAlgorithmException {
        String users = "CREATE TABLE users (id VARCHAR(255) NOT NULL, name VARCHAR(255) NOT NULL, "
                + "email VARCHAR(200) NOT NULL, role VARCHAR(10) NOT NULL, active CHAR(1) NOT NULL, "
                + "updated CHAR(1) NOT NULL, password VARCHAR(200) NOT NULL,  PRIMARY KEY(id));";
        String permissions = "CREATE TABLE permissions (userid VARCHAR(255) NOT NULL, memory VARCHAR(255) NOT NULL, "
                + "canread CHAR(1), canwrite CHAR(1), canexport CHAR(1), PRIMARY KEY(userid, memory));";
        String memories = "CREATE TABLE memories (id VARCHAR(255) NOT NULL, name VARCHAR(255) NOT NULL, "
                + "owner VARCHAR(255), project VARCHAR(255), subject VARCHAR(255), client VARCHAR(255), "
                + "creationDate TIMESTAMP, PRIMARY KEY(id));";
        try (Statement create = conn.createStatement()) {
            create.execute(users);
            create.execute(permissions);
            create.execute(memories);
        }
        conn.commit();
        logger.log(Level.INFO, "Users table created");
        User sysadmin = new User("sysadmin", "secData", "System Administrator", "sysadmin@localhost",
                Constants.SYSTEM_ADMINISTRATOR, true, false);
        addUser(sysadmin);
    }

    public void addUser(User user) throws SQLException, NoSuchAlgorithmException {
        String sql = "INSERT INTO users (id, password, name, email, role, active, updated) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getId());
            stmt.setString(2, Crypto.sha256(user.getPassword()));
            stmt.setNString(3, user.getName());
            stmt.setString(4, user.getEmail());
            stmt.setString(5, user.getRole());
            stmt.setString(6, user.isActive() ? "Y" : "N");
            stmt.setString(7, user.isUpdated() ? "Y" : "N");
            stmt.execute();
        }
        conn.commit();
    }

    public void addMemory(Memory mem, User owner) throws SQLException {
        String sql = "INSERT INTO memories (id, name, owner, project, subject, client, creationDate) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mem.getId());
            stmt.setNString(2, mem.getName());
            stmt.setString(3, owner.getId());
            stmt.setNString(4, mem.getProject());
            stmt.setNString(5, mem.getSubject());
            stmt.setNString(6, mem.getClient());
            stmt.setTimestamp(7, new Timestamp(mem.getCreationDate().getTime()));
            stmt.execute();
        }
        sql = "INSERT INTO permissions (userid, memory, canread, canwrite, canexport) VALUES (?,?,'Y','Y','Y')";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, owner.getId());
            stmt.setString(2, mem.getId());
            stmt.execute();
        }
        conn.commit();
    }

    public User getUser(String userId) throws SQLException {
        User result = null;
        String sql = "SELECT name, email, role, active, updated, password FROM users WHERE id=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getNString(1);
                    String email = rs.getString(2);
                    String role = rs.getString(3);
                    boolean active = rs.getString(4).equals("Y");
                    boolean updated = rs.getString(5).equals("Y");
                    String password = rs.getString(6);
                    result = new User(userId, password, name, email, role, active, updated);
                }
            }
        }
        return result;
    }

    public void removeUser(String id) throws SQLException {
        boolean hasMemories = false;
        String sql = "SELECT id FROM memories WHERE owner=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    hasMemories = true;
                    break;
                }
            }
        }
        if (hasMemories) {
            throw new SQLException("User owns memories");
        }
        sql = "DELETE FROM permissions WHERE userid=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.execute();
        }
        sql = "DELETE FROM users WHERE id=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.execute();
        }
        conn.commit();
    }

    public List<User> getUsers() throws SQLException {
        List<User> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement()) {
            String sql = "SELECT id, name, email, role, active, updated, password FROM users ORDER BY name";
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    String name = rs.getNString(2);
                    String email = rs.getString(3);
                    String role = rs.getString(4);
                    boolean active = rs.getString(5).equals("Y");
                    boolean updated = rs.getString(6).equals("Y");
                    String password = rs.getString(7);
                    result.add(new User(id, password, name, email, role, active, updated));
                }
            }
        }
        return result;
    }

    public void updateUser(User user) throws SQLException {
        String sql = "UPDATE users SET name=?, email=?, role=?, active=?, updated=? WHERE id=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setNString(1, user.getName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getRole());
            stmt.setString(4, user.isActive() ? "Y" : "N");
            stmt.setString(5, user.isUpdated() ? "Y" : "N");
            stmt.setString(6, user.getId());
            stmt.execute();
        }
        conn.commit();
    }

    public void commit() throws SQLException {
        conn.commit();
    }

    public void rollback() throws SQLException {
        conn.rollback();
    }

    public void setPassword(String user, String password) throws NoSuchAlgorithmException, SQLException {
        String sql = "UPDATE users SET password=?, updated='Y' WHERE id=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, Crypto.sha256(password));
            stmt.setString(2, user);
            stmt.executeUpdate();
        }
        conn.commit();
    }

    public JSONArray getMemories(String user) throws SQLException {
        JSONArray array = new JSONArray();
        User who = getUser(user);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String sql = "SELECT id, name, owner, project, subject, client, creationDate FROM memories ORDER BY name";
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    String name = rs.getNString(2);
                    String owner = rs.getString(3);
                    String project = rs.getNString(4);
                    String subject = rs.getNString(5);
                    String client = rs.getNString(6);
                    Timestamp creationDate = rs.getTimestamp(7);
                    JSONObject memory = new JSONObject();
                    memory.put("id", id);
                    memory.put("name", name);
                    memory.put("owner", owner);
                    memory.put("project", project);
                    memory.put("subject", subject);
                    memory.put("client", client);
                    memory.put("creationDate", df.format(new Date(creationDate.getTime())));
                    if (Constants.SYSTEM_ADMINISTRATOR.equals(who.getRole())) {
                        array.put(memory);
                    } else if (user.equals(owner)) {
                        array.put(memory);
                    } else {
                        Permission p = getPermission(id, user);
                        if (p.canRead() || p.canWrite() || p.canExport()) {
                            array.put(memory);
                        }
                    }
                }
            }
        }
        return array;
    }

    public Memory getMemory(String id) throws SQLException {
        Memory memory = null;
        String sql = "SELECT name, project, subject, client, creationDate FROM memories WHERE id=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getNString(1);
                    String project = rs.getNString(2);
                    String subject = rs.getNString(3);
                    String client = rs.getNString(4);
                    Timestamp creationDate = rs.getTimestamp(5);
                    memory = new Memory(id, name, project, subject, client, new Date(creationDate.getTime()), Memory.LOCAL, "", "", "");
                }
            }
        }
        return memory;
    }

    public Permission getPermission(String memory, String user) throws SQLException {
        Permission p = new Permission(user, memory, false, false, false);
        String sql = "SELECT canread, canwrite, canexport FROM permissions WHERE memory=? AND userid=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, memory);
            stmt.setString(2, user);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    p.setRead("Y".equals(rs.getString(1)));
                    p.setWrite("Y".equals(rs.getString(2)));
                    p.setExport("Y".equals(rs.getString(3)));
                }
            }
        }
        return p;
    }

    public void setPermissions(String memory, JSONArray permissions) throws SQLException {
        String sql = "DELETE FROM permissions WHERE memory=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, memory);
            stmt.execute();
        }
        sql = "INSERT INTO permissions (userid, memory, canread, canwrite, canexport) VALUES (?,?,?,?,?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(2, memory);
            for (int i = 0; i < permissions.length(); i++) {
                JSONObject permission = permissions.getJSONObject(i);
                stmt.setString(1, permission.getString("user"));
                stmt.setString(3, permission.getBoolean("read") ? "Y" : "N");
                stmt.setString(4, permission.getBoolean("write") ? "Y" : "N");
                stmt.setString(5, permission.getBoolean("export") ? "Y" : "N");
                stmt.execute();
            }
        }
        conn.commit();
    }

    public List<Permission> getPermissions(String memory) throws SQLException {
        Map<String, Permission> map = new TreeMap<>();
        String sql = "SELECT userid, canread, canwrite, canexport FROM permissions WHERE memory=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, memory);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String user = rs.getString(1);
                    boolean canRead = rs.getString(2).equals("Y");
                    boolean canWrite = rs.getString(3).equals("Y");
                    boolean canExport = rs.getString(4).equals("Y");
                    Permission p = new Permission(user, memory, canRead, canWrite, canExport);
                    map.put(user, p);
                }
            }
        }
        List<String> users = new ArrayList<>();
        sql = "SELECT id FROM users WHERE active='Y'";
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    users.add(rs.getString(1));
                }
            }
        }
        List<Permission> permissions = new ArrayList<>();
        Iterator<String> it = users.iterator();
        while (it.hasNext()) {
            String user = it.next();
            permissions.add(map.containsKey(user) ? map.get(user) : new Permission(user, memory, false, false, false));
        }
        Collections.sort(permissions);
        return permissions;
    }

    public String getOwner(String memory) throws SQLException {
        String owner = "";
        String sql = "SELECT owner FROM memories WHERE id=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, memory);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    owner = rs.getString(1);
                }
            }
        }
        return owner;
    }

    public void removeMemory(String memory) throws SQLException {
        String sql = "DELETE FROM permissions WHERE memory=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, memory);
            stmt.execute();
        }
        sql = "DELETE FROM memories WHERE id=?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, memory);
            stmt.execute();
        }
        conn.commit();
    }
}
