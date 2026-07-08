import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import {
  getStaff,
  createStaff,
  type StaffMember,
  type CreateStaffRequest,
} from "../api";

const BLANK: CreateStaffRequest = { displayName: "", email: "", role: "CARER" };

export default function Staff() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  const [staff, setStaff] = useState<StaffMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [form, setForm] = useState<CreateStaffRequest>(BLANK);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    getStaff()
      .then(setStaff)
      .catch(() => setError("Could not load staff."))
      .finally(() => setLoading(false));
  }, []);

  async function handleSignOut() {
    await signOut();
    navigate("/login", { replace: true });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setFormError(null);
    try {
      const created = await createStaff(form);
      setStaff((prev) => [...prev, created]);
      setForm(BLANK);
    } catch (err: unknown) {
      setFormError(
        err instanceof Error ? err.message : "Failed to create staff member.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <header className="bar">
        <div>
          <h1>Staff</h1>
          <span className="muted">
            {user?.careHome.name} · {user?.displayName} ({user?.role})
          </span>
        </div>
        <div style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
          <Link to="/residents">Residents</Link>
          <button className="link" onClick={handleSignOut}>
            Sign out
          </button>
        </div>
      </header>

      <div className="card" style={{ marginTop: "1.5rem" }}>
        <h2>Role permissions</h2>
        <table>
          <thead>
            <tr>
              <th>Feature</th>
              <th>Admin</th>
              <th>Carer</th>
            </tr>
          </thead>
          <tbody>
            <tr><td>View residents &amp; care log</td><td>Yes</td><td>Yes</td></tr>
            <tr><td>Add care log entry</td><td>Yes</td><td>Yes</td></tr>
            <tr><td>Edit care log entry</td><td>Yes</td><td>No</td></tr>
            <tr><td>Delete care log entry</td><td>Yes</td><td>No</td></tr>
            <tr><td>Add resident</td><td>Yes</td><td>No</td></tr>
            <tr><td>Edit resident</td><td>Yes</td><td>No</td></tr>
            <tr><td>Delete resident</td><td>Yes</td><td>No</td></tr>
            <tr><td>View staff list</td><td>Yes</td><td>No</td></tr>
            <tr><td>Add staff member</td><td>Yes</td><td>No</td></tr>
            <tr><td>Delete staff member</td><td>No</td><td>No</td></tr>
          </tbody>
        </table>
        <p className="muted" style={{ marginTop: "0.75rem", fontSize: "0.85em" }}>
          Admins can manage care logs and onboard new staff. Carers have read access and can add new log entries. No role can delete staff members — contact your system administrator.
        </p>
      </div>

      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error">{error}</p>}

      {!loading && !error && (
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Auth0 linked</th>
            </tr>
          </thead>
          <tbody>
            {staff.map((s) => (
              <tr key={s.id}>
                <td>{s.displayName}</td>
                <td>{s.email}</td>
                <td>{s.role}</td>
                <td>{s.linked ? "Yes" : "Pending"}</td>
              </tr>
            ))}
            {staff.length === 0 && (
              <tr>
                <td colSpan={4} className="muted">
                  No staff yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}

      <section style={{ marginTop: "2rem" }}>
        <h2>Add staff member</h2>
        <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "0.75rem", maxWidth: "360px" }}>
          <label>
            Name
            <input
              type="text"
              value={form.displayName}
              required
              onChange={(e) => setForm({ ...form, displayName: e.target.value })}
            />
          </label>
          <label>
            Email
            <input
              type="email"
              value={form.email}
              required
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </label>
          <label>
            Role
            <select
              value={form.role}
              onChange={(e) =>
                setForm({ ...form, role: e.target.value as "ADMIN" | "CARER" })
              }
            >
              <option value="CARER">Carer</option>
              <option value="ADMIN">Admin</option>
            </select>
          </label>
          {formError && <p className="error">{formError}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? "Creating…" : "Create staff member"}
          </button>
        </form>
        <p className="muted" style={{ marginTop: "0.5rem" }}>
          The new staff member will receive an email to set their password.
        </p>
      </section>
    </>
  );
}
