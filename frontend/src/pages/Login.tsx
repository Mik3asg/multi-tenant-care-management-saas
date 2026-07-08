export default function Login() {
  return (
    <div className="card" style={{ maxWidth: 420, margin: "40px auto" }}>
      <h1>Care Management</h1>
      <p className="muted">Sign in to your care home.</p>
      <a href="/oauth2/authorization/auth0">
        <button style={{ width: "100%" }}>Sign in</button>
      </a>
    </div>
  );
}
