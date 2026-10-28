package edu.classroom.board;

import io.jsonwebtoken.Claims; //토큰 안의 내용
import io.jsonwebtoken.ExpiredJwtException; //만료된 토큰일 때 발생하는 오류
import io.jsonwebtoken.JwtException; //그 외 토큰 오류
import io.jsonwebtoken.Jwts;//토큰을 만드는 거
import io.jsonwebtoken.security.Keys; //키 만드는거
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant; //지금 시간을 알려줌
import java.util.Date; //만료 시각을 알려줌
import java.util.HexFormat;//16진수로
import java.util.regex.Matcher; //문자열 모양 검사 결과
import java.util.regex.Pattern; //문자열 모양 검사 규칙
import javax.crypto.SecretKey;//키의 종류
import org.springframework.beans.factory.annotation.Value; //설정값 받기
import org.springframework.stereotype.Service;

// 인증 클래스. 토큰을 발급(issueToken), 요청의 토큰을 검사해 사용자를 알아내고(currentUserId), 글 작성자를 확인한다(assertOwner)
@Service
public class AuthService {
  @Value("${app.jwt.secret}") //jwt.secret라는 곳에섯 설정값을 가져와섯 밑의 secret이라는 칸에 넣어줘
  private String secret;//받을 칸의 종류와 이름
  @Value("${app.jwt.issuer}")
  private String issuer; //발급자
  @Value("${app.jwt.audience}")
  private String audience; //사용 대상
  @Value("${app.jwt.ttl}")
  private long ttl; //토큰 유효 시간(초). 기본값 90, TOKEN_TTL로 바꿀 수 있다

  //Bearer 토큰 모양인지 검사
  private static final Pattern BEARER = Pattern.compile("^Bearer ([^\\s]+)$");

  //토큰이 가리키는 사용자가 실제로 있는지 확인하려고 Users(계정 목록)를 연결한다
  private final Users users;

  // 생성자 주입: 스프링이 만들어 둔 Users 객체를 자동으로 넣어 준다
  public AuthService(Users users) {
    this.users = users; // 받은 Users를 위의 users 칸에 보관
  }
  //서버가 켜질 때 스프링이 설정 파일과 환경변수를 읽어서, 이 네 칸에 값을 자동으로 채워 주고 그 칸을 issueToken 안에서 꺼내 쓰기
  //서명에 쓸 비밀 키 만들기 토큰 발급(issueToken)과 검사(currentUserId)가 같은 키를 쓴다(HS256)
  //키가 비었거나 64자리 16진수가 아니면 키를 만들지 않고 오류를 던진다
  private SecretKey signingKey() {
    if (secret == null || !secret.matches("[0-9a-fA-F]{64}")) { //특정 표준에 맞지 않으면 오류가 발생
      throw new IllegalStateException("JWT_SECRET은 64자리 16진수여야 합니다."); // 잘못된 설정이면 여기서 멈춤
    }
    return Keys.hmacShaKeyFor(HexFormat.of().parseHex(secret));//키 객체를 만듦.
  }

  // [BE-01] 로그인 성공한 사람에게 토큰 주기 BoardController.login호출
  //토큰 안에는 사용자 비밀번호와 해시는 넣지 않음
  public String issueToken(Users.PublicUser user) { //매서드 시작
    Instant now = Instant.now(); //지금 시간
    return Jwts.builder() //토큰 만들기 시작ㄱ
        .subject(String.valueOf(user.id()))//누구인지 사용자 번호를 문자열로
        .issuer(issuer)//발급한 곳
        .audience()//이 토큰을 쓸곳
        .single(audience) // aud를 문자열 하나로 기록
        .issuedAt(Date.from(now))//발급 시간
        .expiration(Date.from(now.plusSeconds(ttl)))//지금부터 90초 동안 유효
        .signWith(signingKey(), Jwts.SIG.HS256) //서명에 쓸 방식 지정
        .compact(); // 헤더.페이로드.서명 을 점으로 이어 문자열 하나로 완성 (이때 서명이 계산됨)
  }

  // 요청 헤더에서 Bearer 토큰만 꺼내기 헤더가 없거나 모양이 다르면 null
  //Bearer 토큰이란 이 토큰을 가지고 있는 사람에게 리소스에 접근할 권한을 부여한다
  private String readBearer(HttpServletRequest request) {
    String header = request.getHeader("Authorization"); // 요청 헤더 Authorization: Bearer <JWT>를 읽음
    if (header == null) return null; // 헤더 자체가 없으면 토큰 없음
    Matcher m = BEARER.matcher(header); // Bearer 토큰 모양인지 검사
    return m.matches() ? m.group(1) : null; // 모양이 맞으면 토큰 부분만, 아니면 null
  }

  // 401 오류 객체를 만듦
  private ApiError unauthorized(String code, String message) {
    return new ApiError(401, code, message);
  }

  // [BE-02] 요청에 실린 JWT를 검사해 이 요청을 보낸 사용자 번호를 돌려준다.
  public long currentUserId(HttpServletRequest request) {
    //헤더에서 토큰 꺼내기. 없으면 TOKEN_REQUIRED
    String token = readBearer(request);
    if (token == null) throw unauthorized("TOKEN_REQUIRED", "토큰이 없거나 형식이 다름");

    long userId; //검증이 끝나면 토큰의 사용자번호 값을 숫자로 바꿔 여기에 담음
    try {
      //서명 만료 발급자 대상을 한 번에 검증 (decode가 아니라 verify)
      var jws = //검증을 통과한 토큰(헤더+내용)을 담는다. 실패하면 아래 catch로 이동
          Jwts.parser() // 토큰 검사기를 만들기 시작
              .verifyWith(signingKey()) // 서버 비밀 키로 서명을 다시 계산해 비교 (변조 여부 확인)
              .requireIssuer(issuer) //발급자가 webservice-class가 아니면 거부
              .requireAudience(audience) //사용자가 board-api가 아니면 거부
              .build() // 검사기 완성
              .parseSignedClaims(token); //변조여부 확인(토큰의 서명과 비교)

      //HS256 하나로 고정
      if (!"HS256".equals(jws.getHeader().getAlgorithm())) {
        throw unauthorized("INVALID_TOKEN", "올바르지 않은 토큰입니다.");
      }

      //이 서비스가 요구하는 값(exp 존재, sub는 양의 정수 문자열) 확인
      //서명이 맞아도 이 서비스가 요구하는 값이 없으면 거부함. exp유무, sub는 양의정수 문자열
      Claims claims = jws.getPayload(); //토큰의 내용(페이로드)
      String sub = claims.getSubject(); //누구인지(사용자 번호 문자열). 없으면 null
      if (claims.getExpiration() == null || sub == null || !sub.matches("[1-9][0-9]{0,15}")) {
        throw unauthorized("INVALID_TOKEN", "올바르지 않은 토큰입니다.");
      }
      userId = Long.parseLong(sub); // 유저id의 문자열을 숫자로 변환
    } catch (ExpiredJwtException e) {
      // 서명은 맞는데 시간이 지난 토큰
      throw unauthorized("TOKEN_EXPIRED", "로그인 시간 만료");
    } catch (JwtException | IllegalArgumentException e) {
      // 변조, 서명 오류, 형식 오류, 발급자, 사용대상 불일치 등
      throw unauthorized("INVALID_TOKEN", "올바르지 않은 토큰입니다.");
    }

    //토큰이 가리키는 사용자가 실제로 있는지 확인
    if (users.find(userId) == null) {
      throw unauthorized("INVALID_TOKEN", "올바르지 않은 토큰입니다.");
    }
    return userId; //모든 검사를 통과한 사용자 번호
  }

  public void assertOwner(long userId, long authorId) {//지금 요청한 사용자의 id, db에 저장된 해당 글은 쓴 사람의 번호 이 둘을 비교
    // [BE-03]
    if (userId != authorId) {
      throw new ApiError(403, "FORBIDDEN", "본인이 작성한 글만 수정 및 삭제 가능");
    }
  }
}
