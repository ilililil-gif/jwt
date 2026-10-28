package edu.classroom.board;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BoardController {
  private final PostStore store;
  private final AuthService auth;
  private final Users users;

  @Value("${app.jwt.ttl}")
  private int ttl;

  public BoardController(PostStore store, AuthService auth, Users users) {
    this.store = store;
    this.auth = auth;
    this.users = users;
  }

  private Map<String, String> fields(Map<String, Object> body, Map<String, Integer> limits) {
    if (body == null || !limits.keySet().containsAll(body.keySet()))
      throw new ApiError(400, "BAD_INPUT", "허용된 JSON 필드만 보내세요.");
    Map<String, String> out = new HashMap<>();
    for (var e : limits.entrySet()) {
      Object v = body.get(e.getKey());
      if (!(v instanceof String)) throw new ApiError(400, "BAD_INPUT", "문자열을 입력하세요.");
      String s = (String) v, t = s.strip();
      if (t.isEmpty() || t.codePointCount(0, t.length()) > e.getValue())
        throw new ApiError(400, "BAD_INPUT", e.getKey() + " 입력을 확인하세요.");
      out.put(e.getKey(), e.getKey().equals("password") ? s : t);
    }
    return out;
  }

  private long postId(String value) {
    try {
      if (!value.matches("[1-9][0-9]*")) throw new NumberFormatException();
      long id = Long.parseLong(value);
      if (id > 9007199254740991L) throw new NumberFormatException();
      return id;
    } catch (NumberFormatException e) {
      throw new ApiError(400, "BAD_INPUT", "ID는 양의 정수여야 합니다.");
    }
  }

  private PostStore.Post existing(long id) {//계시글의 존재 확인
    var p = store.get(id);
    if (p == null) throw new ApiError(404, "NOT_FOUND", "게시글이 없습니다."); //계시글이 존재하지 않을 경우
    return p;
  }

  @GetMapping("/health")
  public Object health() {
    return Map.of("status", "ok", "stack", "spring");
  }

  @PostMapping("/auth/login")
  public Object login(@RequestBody Map<String, Object> body) {
    var input = fields(body, Map.of("email", 254, "password", 128)); //email, password 꺼냄
    var user = users.verify(input.get("email"), input.get("password"));//계정,비밀번호 확인
    if (user == null) throw new ApiError(401, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인하세요.");//틀리면 401
    return Map.of("accessToken", auth.issueToken(user), "expiresIn", ttl, "user", user);//토큰 발급 호출 및 응답
  }

  @GetMapping("/me")
  public Object me(HttpServletRequest req) {
    var user = users.find(auth.currentUserId(req));
    if (user == null) throw new ApiError(401, "INVALID_TOKEN", "사용자를 확인할 수 없습니다.");
    return user;
  }

  @GetMapping("/posts")
  public Object list() {
    return store.list();
  }

  @GetMapping("/posts/{id}")
  public Object get(@PathVariable String id) {
    return existing(postId(id));
  }

  @PostMapping("/posts")
  public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    long user = auth.currentUserId(req);
    var input = fields(body, Map.of("title", 100, "content", 5000));
    var p = store.create(input.get("title"), input.get("content"), user);
    return ResponseEntity.created(URI.create("/api/posts/" + p.id())).body(p);
  }

  @PutMapping("/posts/{id}") //put /api/posts/글의 번호 요청이 오면 이 함수 사용
  public Object update(
      @PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest req) { //글번호를 id로 받음. 요청의 json을 body로 받음.
    long user = auth.currentUserId(req), pid = postId(id); //토큰을 검증해서 요청한 사람의 번호를 user에 담음. 토큰이 없거나 이상하면 401. postId(id)주소의 글번호가 양의 정수 모양인지 확인
    var p = existing(pid);//데이터베이스에서 그 번호의 글을 찾아 p에 담기.
    auth.assertOwner(user, p.authorId()); //요청한 유저와 글쓴이(p.autorId())를 비교함. 다르면 403 같으면 다음줄로
    var input = fields(body, Map.of("title", 100, "content", 5000));//본문 형식이 맞는지 확인 틀리면 400
    return store.update(pid, input.get("title"), input.get("content"));//이러한 검사를 통과했으면 데이터베이스의 글을 새 제목과 내용으로 바꿈
  }

  @DeleteMapping("/posts/{id}")
  public ResponseEntity<?> delete(@PathVariable String id, HttpServletRequest req) {
    long user = auth.currentUserId(req), pid = postId(id);
    var p = existing(pid);
    auth.assertOwner(user, p.authorId());
    store.delete(pid);
    return ResponseEntity.noContent().build();
  }
}
