package com.resort.platform.users;

import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.auth.SessionInvalidator;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.Emails;
import com.resort.platform.common.PageResponse;
import com.resort.platform.prospectors.Prospector;
import com.resort.platform.prospectors.ProspectorRepository;
import com.resort.platform.users.dto.CreateUserRequest;
import com.resort.platform.users.dto.CreatedUserResponse;
import com.resort.platform.users.dto.UpdateUserRequest;
import com.resort.platform.users.dto.UserResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class UserService {

    static final String ENTITY_TYPE = "USER";

    private final UserRepository users;
    private final ProspectorRepository prospectors;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswords;
    private final AuditService audit;
    private final SessionInvalidator sessions;

    public UserService(
            UserRepository users,
            ProspectorRepository prospectors,
            PasswordEncoder passwordEncoder,
            TemporaryPasswordGenerator temporaryPasswords,
            AuditService audit,
            SessionInvalidator sessions) {
        this.users = users;
        this.prospectors = prospectors;
        this.passwordEncoder = passwordEncoder;
        this.temporaryPasswords = temporaryPasswords;
        this.audit = audit;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        Page<User> page = users.findAll(
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name", "id")));
        Map<UUID, Prospector> prospectorsByUser = prospectors
                .findByUserIdIn(page.getContent().stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(prospector -> prospector.getUser().getId(), Function.identity()));
        return PageResponse.of(page, user -> UserResponse.of(user, prospectorsByUser.get(user.getId())));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        User user = find(id);
        return UserResponse.of(user, prospectors.findByUserId(id).orElse(null));
    }

    public CreatedUserResponse create(CreateUserRequest request) {
        String email = Emails.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw emailAlreadyExists();
        }
        String employeeCode = trimToNull(request.employeeCode());
        String phone = trimToNull(request.phone());
        if (request.role() == Role.PROSPECTOR) {
            if (employeeCode == null) {
                throw ApiException.badRequest("VALIDATION_ERROR", "O código de funcionário é obrigatório para PROSPECTOR.");
            }
            if (prospectors.existsByEmployeeCode(employeeCode)) {
                throw employeeCodeAlreadyExists();
            }
        } else if (employeeCode != null || phone != null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Código de funcionário e telefone só se aplicam a PROSPECTOR.");
        }

        String temporaryPassword = temporaryPasswords.generate();
        User user = users.save(new User(
                request.name().trim(), email, passwordEncoder.encode(temporaryPassword), request.role(), true));
        Prospector prospector = request.role() == Role.PROSPECTOR
                ? prospectors.save(new Prospector(user, employeeCode, phone))
                : null;
        audit.record(AuditAction.USER_CREATED, ENTITY_TYPE, user.getId(), Map.of("role", user.getRole().name()));
        return new CreatedUserResponse(UserResponse.of(user, prospector), temporaryPassword);
    }

    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = find(id);
        List<String> changedFields = new ArrayList<>();

        String email = Emails.normalize(request.email());
        if (!email.equals(user.getEmail())) {
            if (users.existsByEmailAndIdNot(email, id)) {
                throw emailAlreadyExists();
            }
            user.setEmail(email);
            changedFields.add("email");
        }
        String name = request.name().trim();
        if (!name.equals(user.getName())) {
            user.setName(name);
            changedFields.add("name");
        }
        boolean roleChanged = request.role() != user.getRole();
        if (roleChanged) {
            // D-042: PROSPECTOR não troca de role; carteira e visitas dependem do registro em prospectors.
            if (user.getRole() == Role.PROSPECTOR || request.role() == Role.PROSPECTOR) {
                throw ApiException.conflict("ROLE_CHANGE_NOT_ALLOWED",
                        "Troca de perfil permitida apenas entre ADMIN, GATE e HOST.");
            }
            if (user.getRole() == Role.ADMIN && user.isActive()) {
                ensureNotLastActiveAdmin(user);
            }
            user.setRole(request.role());
            changedFields.add("role");
        }

        if (!changedFields.isEmpty()) {
            audit.record(AuditAction.USER_UPDATED, ENTITY_TYPE, id, Map.of("changedFields", changedFields));
        }
        if (roleChanged) {
            sessions.invalidateAll(id);
        }
        return UserResponse.of(user, prospectors.findByUserId(id).orElse(null));
    }

    public UserResponse changeStatus(UUID id, boolean active) {
        User user = find(id);
        if (user.isActive() != active) {
            if (!active && user.getRole() == Role.ADMIN) {
                ensureNotLastActiveAdmin(user);
            }
            user.setActive(active);
            audit.record(AuditAction.USER_STATUS_CHANGED, ENTITY_TYPE, id, Map.of("active", active));
            if (!active) {
                sessions.invalidateAll(id);
            }
        }
        return UserResponse.of(user, prospectors.findByUserId(id).orElse(null));
    }

    public String resetPassword(UUID id) {
        User user = find(id);
        String temporaryPassword = temporaryPasswords.generate();
        user.changePassword(passwordEncoder.encode(temporaryPassword), true);
        audit.record(AuditAction.PASSWORD_RESET, ENTITY_TYPE, id, null);
        sessions.invalidateAll(id);
        return temporaryPassword;
    }

    /** D-048: sempre resta ao menos um ADMIN ativo. Os ADMINs ativos ficam bloqueados até o fim da transação. */
    private void ensureNotLastActiveAdmin(User user) {
        List<User> activeAdmins = users.findByRoleAndActiveTrue(Role.ADMIN);
        boolean isOnlyActiveAdmin = activeAdmins.stream().allMatch(admin -> admin.getId().equals(user.getId()));
        if (isOnlyActiveAdmin) {
            throw ApiException.conflict("LAST_ADMIN", "Não é possível remover o último administrador ativo.");
        }
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Usuário não encontrado."));
    }

    private static ApiException emailAlreadyExists() {
        return ApiException.conflict("EMAIL_ALREADY_EXISTS", "Já existe um usuário com este e-mail.");
    }

    static ApiException employeeCodeAlreadyExists() {
        return ApiException.conflict("EMPLOYEE_CODE_ALREADY_EXISTS", "Já existe um Prospector com este código.");
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
