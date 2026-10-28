// TODO FE-01~04: 공통 API 명세와 구현 체크리스트를 보고 완성하세요.
let session = { accessToken: "", user: null };
export const getSession = () => session;
export function clearSession() {
  session = { accessToken: "", user: null };
}
function authorizationHeaders() {
  // TODO FE-02: 메모리에 보관한 토큰을 Bearer 헤더로 보내세요.
  const token = session.accessToken; //로그인 시 저장한 토큰을 token에 넣기
  return token ? { Authorization: `Bearer ${token}` } : {}; //token이 있으면 헤더를 만들어 리턴. 없으면 그냥 {} 이기에 헤더를 만들지 않음
}
export async function request(path, options = {}) { //프론트에서 서버로 가는 요청들이 지나가는 공통함수
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...authorizationHeaders(),
      ...options.headers,
    },
  });
  const data = response.status === 204 ? null : await response.json();
  if (!response.ok) {
    // TODO FE-03: 401이면 메모리의 로그인 상태도 해제하세요.
    if (response.status === 401) clearSession();
    const error = new Error(
      data.message || data.code || `HTTP ${response.status}`,
    );
    error.status = response.status;
    error.code = data.code;
    throw error;
  }
  return data;
}

export async function login(email, password) { //이메일과 비밀번홓를 받아서 로그인 하는 부분 ansync는 서버 응답을 기다리는 함수 라는 뜻
  // TODO FE-01: POST /auth/login → 응답의 토큰과 사용자 정보를 session에 저장.
  // request()를 이용할 수 있습니다. 성공하면 user를 반환하세요.
  const data = await request("/auth/login", { //awiat는 응답이 올 때까지 기다린다음 다음 줄로 가라 라는 뜻. 응답은 당연히 data에
    method: "POST",//로그인은 데이터를 보내는 것이므로 post
    body: JSON.stringify({ email, password }), //JSON.stringify는 그 객체를 서버가 읽는 json글자로 바꿔줌
  });
  session = { accessToken: data.accessToken, user: data.user };//토큰과 사용자id를 session변수에 저장
  return data.user;//사용자 id를 리턴
}
export function canEdit(post) {
  // TODO FE-04: 로그인 사용자와 작성자를 비교하세요. 서버 검사도 반드시 필요합니다.
  return Boolean(session.user) && post.authorId === session.user.id;
}
export const api = {
  list: () => request("/posts"),
  detail: (id) => request(`/posts/${id}`),
  create: (input) =>
    request("/posts", { method: "POST", body: JSON.stringify(input) }),
  update: (id, input) =>
    request(`/posts/${id}`, { method: "PUT", body: JSON.stringify(input) }),
  remove: (id) => request(`/posts/${id}`, { method: "DELETE" }),
  me: () => request("/me"),
};
