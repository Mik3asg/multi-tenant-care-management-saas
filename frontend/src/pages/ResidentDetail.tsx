import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import {
  addCareLog,
  updateCareLog,
  deleteCareLog,
  updateResident,
  deleteResident,
  getResident,
  ApiError,
  type ResidentDetail as ResidentDetailType,
  type CareLogEntry,
} from "../api";

const CATEGORIES = ["MEDICATION", "MEAL", "OBSERVATION", "PERSONAL_CARE", "INCIDENT"];

export default function ResidentDetail() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user?.role === "ADMIN";

  const [resident, setResident] = useState<ResidentDetailType | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Add form state
  const [category, setCategory] = useState(CATEGORIES[0]);
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  // Resident edit state
  const [editingResident, setEditingResident] = useState(false);
  const [residentForm, setResidentForm] = useState({ fullName: "", dateOfBirth: "", room: "" });
  const [residentSaving, setResidentSaving] = useState(false);
  const [residentError, setResidentError] = useState<string | null>(null);

  // Inline edit state — tracks which log entry is being edited
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editCategory, setEditCategory] = useState("");
  const [editNote, setEditNote] = useState("");
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  function load(residentId: string) {
    setLoading(true);
    getResident(residentId)
      .then(setResident)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setError("Resident not found.");
        } else {
          setError("Could not load resident.");
        }
      })
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    if (id) load(id);
  }, [id]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!id) return;
    setFormError(null);
    setSaving(true);
    try {
      await addCareLog(id, category, note.trim());
      setNote("");
      load(id);
    } catch {
      setFormError("Could not save the entry.");
    } finally {
      setSaving(false);
    }
  }

  function startEditResident() {
    if (!resident) return;
    setResidentForm({
      fullName: resident.fullName,
      dateOfBirth: resident.dateOfBirth,
      room: resident.room ?? "",
    });
    setResidentError(null);
    setEditingResident(true);
  }

  async function saveResident() {
    if (!id) return;
    setResidentSaving(true);
    setResidentError(null);
    try {
      await updateResident(id, residentForm.fullName, residentForm.dateOfBirth, residentForm.room);
      setEditingResident(false);
      load(id);
    } catch {
      setResidentError("Could not save changes.");
    } finally {
      setResidentSaving(false);
    }
  }

  async function handleDeleteResident() {
    if (!id || !resident) return;
    if (!window.confirm(`Delete ${resident.fullName} and all their care log entries? This cannot be undone.`)) return;
    await deleteResident(id);
    navigate("/residents", { replace: true });
  }

  function startEdit(entry: CareLogEntry) {
    setEditingId(entry.id);
    setEditCategory(entry.category);
    setEditNote(entry.note);
    setEditError(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditError(null);
  }

  async function saveEdit(entry: CareLogEntry) {
    if (!id) return;
    setEditSaving(true);
    setEditError(null);
    try {
      await updateCareLog(id, entry.id, editCategory, editNote.trim());
      setEditingId(null);
      load(id);
    } catch {
      setEditError("Could not save changes.");
    } finally {
      setEditSaving(false);
    }
  }

  async function handleDelete(logId: string) {
    if (!id) return;
    if (!window.confirm("Delete this care log entry? This cannot be undone.")) return;
    try {
      await deleteCareLog(id, logId);
      load(id);
    } catch {
      // show inline error on the entry — for simplicity just reload
      load(id);
    }
  }

  if (loading) return <p className="muted">Loading…</p>;
  if (error) {
    return (
      <>
        <p className="error">{error}</p>
        <Link to="/residents">← Back to residents</Link>
      </>
    );
  }
  if (!resident) return null;

  return (
    <>
      <header className="bar">
        <h1>{resident.fullName}</h1>
        <Link to="/residents">← Residents</Link>
      </header>

      <div className="card">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h2>Care information</h2>
          {isAdmin && !editingResident && (
            <div style={{ display: "flex", gap: "0.75rem" }}>
              <button className="link" onClick={startEditResident}>Edit</button>
              <button className="link" style={{ color: "var(--error, #c0392b)" }} onClick={handleDeleteResident}>Delete resident</button>
            </div>
          )}
        </div>
        {editingResident ? (
          <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", maxWidth: "360px" }}>
            <label>
              Full name
              <input
                type="text"
                required
                value={residentForm.fullName}
                onChange={(e) => setResidentForm({ ...residentForm, fullName: e.target.value })}
              />
            </label>
            <label>
              Date of birth
              <input
                type="date"
                required
                value={residentForm.dateOfBirth}
                onChange={(e) => setResidentForm({ ...residentForm, dateOfBirth: e.target.value })}
              />
            </label>
            <label>
              Room (optional)
              <input
                type="text"
                value={residentForm.room}
                onChange={(e) => setResidentForm({ ...residentForm, room: e.target.value })}
              />
            </label>
            {residentError && <p className="error">{residentError}</p>}
            <div style={{ display: "flex", gap: "0.5rem" }}>
              <button onClick={saveResident} disabled={residentSaving}>
                {residentSaving ? "Saving…" : "Save"}
              </button>
              <button className="link" onClick={() => setEditingResident(false)}>Cancel</button>
            </div>
          </div>
        ) : (
          <p>
            <strong>Room:</strong> {resident.room ?? "—"}
            <br />
            <strong>Date of birth:</strong> {resident.dateOfBirth}
          </p>
        )}
      </div>

      <div className="card">
        <h2>Add care log entry</h2>
        <form onSubmit={onSubmit}>
          <label htmlFor="category">Category</label>
          <select
            id="category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
          <label htmlFor="note">Note</label>
          <textarea
            id="note"
            rows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
          {formError && <p className="error">{formError}</p>}
          <button type="submit" disabled={saving || note.trim().length === 0}>
            {saving ? "Saving…" : "Add entry"}
          </button>
        </form>
        {!isAdmin && (
          <p className="muted" style={{ marginTop: "0.5rem", fontSize: "0.85em" }}>
            As a Carer you can add entries. Editing and deleting entries requires Admin access.
          </p>
        )}
      </div>

      <div className="card">
        <h2>Care log</h2>
        {resident.careLog.length === 0 && (
          <p className="muted">No entries yet.</p>
        )}
        {resident.careLog.map((entry) =>
          editingId === entry.id ? (
            <div className="log-entry" key={entry.id}>
              <select
                value={editCategory}
                onChange={(e) => setEditCategory(e.target.value)}
                style={{ marginBottom: "0.5rem" }}
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
              <textarea
                rows={3}
                value={editNote}
                onChange={(e) => setEditNote(e.target.value)}
                style={{ display: "block", width: "100%", marginBottom: "0.5rem" }}
              />
              {editError && <p className="error">{editError}</p>}
              <button
                onClick={() => saveEdit(entry)}
                disabled={editSaving || editNote.trim().length === 0}
              >
                {editSaving ? "Saving…" : "Save"}
              </button>{" "}
              <button className="link" onClick={cancelEdit}>Cancel</button>
            </div>
          ) : (
            <div className="log-entry" key={entry.id}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div>
                  <span className="badge">{entry.category}</span>{" "}
                  <span className="muted">
                    {new Date(entry.createdAt).toLocaleString()} · {entry.authorName}
                  </span>
                </div>
                {isAdmin && (
                  <div style={{ display: "flex", gap: "0.5rem", flexShrink: 0, marginLeft: "1rem" }}>
                    <button className="link" onClick={() => startEdit(entry)}>Edit</button>
                    <button className="link" style={{ color: "var(--error, #c0392b)" }} onClick={() => handleDelete(entry.id)}>Delete</button>
                  </div>
                )}
              </div>
              <p style={{ margin: "6px 0 0" }}>{entry.note}</p>
            </div>
          )
        )}
      </div>
    </>
  );
}
