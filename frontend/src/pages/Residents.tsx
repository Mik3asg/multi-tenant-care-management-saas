import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import {
  getResidents,
  createResident,
  deleteResident,
  type ResidentSummary,
} from "../api";

const BLANK = { fullName: "", dateOfBirth: "", room: "" };

export default function Residents() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user?.role === "ADMIN";

  const [residents, setResidents] = useState<ResidentSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(BLANK);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    getResidents()
      .then(setResidents)
      .catch(() => setError("Could not load residents."))
      .finally(() => setLoading(false));
  }

  useEffect(() => { load(); }, []);

  async function handleSignOut() {
    await signOut();
    navigate("/login", { replace: true });
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setFormError(null);
    try {
      await createResident(form.fullName, form.dateOfBirth, form.room);
      setForm(BLANK);
      setShowForm(false);
      load();
    } catch {
      setFormError("Could not create resident.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: string, name: string) {
    if (!window.confirm(`Delete ${name} and all their care log entries? This cannot be undone.`)) return;
    try {
      await deleteResident(id);
      load();
    } catch {
      setError("Could not delete resident.");
    }
  }

  return (
    <>
      <header className="bar">
        <div>
          <h1>Residents</h1>
          <span className="muted">
            {user?.careHome.name} · {user?.displayName} ({user?.role})
          </span>
        </div>
        <div style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
          {isAdmin && <Link to="/staff">Staff</Link>}
          <button className="link" onClick={handleSignOut}>Sign out</button>
        </div>
      </header>

      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error">{error}</p>}

      {!loading && !error && (
        <>
          {isAdmin && (
            <div style={{ marginBottom: "1rem" }}>
              {!showForm ? (
                <button onClick={() => setShowForm(true)}>+ Add resident</button>
              ) : (
                <div className="card">
                  <h2>New resident</h2>
                  <form onSubmit={handleCreate} style={{ display: "flex", flexDirection: "column", gap: "0.75rem", maxWidth: "360px" }}>
                    <label>
                      Full name
                      <input
                        type="text"
                        required
                        value={form.fullName}
                        onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                      />
                    </label>
                    <label>
                      Date of birth
                      <input
                        type="date"
                        required
                        value={form.dateOfBirth}
                        onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })}
                      />
                    </label>
                    <label>
                      Room (optional)
                      <input
                        type="text"
                        value={form.room}
                        onChange={(e) => setForm({ ...form, room: e.target.value })}
                      />
                    </label>
                    {formError && <p className="error">{formError}</p>}
                    <div style={{ display: "flex", gap: "0.5rem" }}>
                      <button type="submit" disabled={saving}>
                        {saving ? "Saving…" : "Create resident"}
                      </button>
                      <button type="button" className="link" onClick={() => { setShowForm(false); setForm(BLANK); }}>
                        Cancel
                      </button>
                    </div>
                  </form>
                </div>
              )}
            </div>
          )}

          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Room</th>
                <th>Date of birth</th>
                {isAdmin && <th></th>}
              </tr>
            </thead>
            <tbody>
              {residents.map((r) => (
                <tr key={r.id}>
                  <td><Link to={`/residents/${r.id}`}>{r.fullName}</Link></td>
                  <td>{r.room ?? "—"}</td>
                  <td>{r.dateOfBirth}</td>
                  {isAdmin && (
                    <td>
                      <button
                        className="link"
                        style={{ color: "var(--error, #c0392b)" }}
                        onClick={() => handleDelete(r.id, r.fullName)}
                      >
                        Delete
                      </button>
                    </td>
                  )}
                </tr>
              ))}
              {residents.length === 0 && (
                <tr>
                  <td colSpan={isAdmin ? 4 : 3} className="muted">No residents yet.</td>
                </tr>
              )}
            </tbody>
          </table>
        </>
      )}
    </>
  );
}
