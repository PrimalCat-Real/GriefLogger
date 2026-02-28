package com.daqem.grieflogger.database.service;

import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.repository.UserRepository;
import java.util.Map;
import java.util.UUID;

public class UserService {

    private final UserRepository userRepository;
    private final UsernameService usernameService;

    public UserService(Database database) {
        this.userRepository = new UserRepository(database);
        this.usernameService = new UsernameService(database);
    }

    public void createTable() {
        userRepository.createTable();
    }

    public void insertOrUpdateName(UUID uuid, String name) {
        userRepository.insertOrUpdateName(name,uuid.toString());
        usernameService.insert(uuid, name);
    }

    public Map<Integer, String> getAllUsernames() {
        return userRepository.getAllUsernames();
    }

    public java.util.List<String> getUuidsByIds(java.util.Collection<Integer> ids) {
        return userRepository.getUuidsByIds(ids);
    }

    /**
     * Insert a phantom user (e.g., #chute, #hopper) if it doesn't exist.
     * Phantom users represent automated/mechanical transfers.
     */
    public void insertPhantomUser(String phantomName) {
        userRepository.insertPhantomUser(phantomName);
    }
}
