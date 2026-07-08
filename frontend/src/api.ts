// Thin fetch wrapper. All requests send the session cookie (credentials:
// "include"); the server derives the tenant from that session.

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export interface CareHome {
  id: string;
  name: string;
  slug: string;
}

export interface Me {
  userId: string;
  email: string;
  displayName: string;
  role: "ADMIN" | "CARER";
  careHome: CareHome;
}

export interface ResidentSummary {
  id: string;
  fullName: string;
  dateOfBirth: string;
  room: string | null;
}

export interface CareLogEntry {
  id: string;
  createdAt: string;
  category: string;
  note: string;
  authorName: string;
}

export interface ResidentDetail {
  id: string;
  fullName: string;
  dateOfBirth: string;
  room: string | null;
  careLog: CareLogEntry[];
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      if (body && typeof body.error === "string") {
        message = body.error;
      }
    } catch {
      // no JSON body
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

const jsonHeaders = { "Content-Type": "application/json" };

export async function login(email: string, password: string): Promise<Me> {
  const res = await fetch("/api/auth/login", {
    method: "POST",
    credentials: "include",
    headers: jsonHeaders,
    body: JSON.stringify({ email, password }),
  });
  return handle<Me>(res);
}

export async function logout(): Promise<void> {
  await fetch("/api/auth/logout", { method: "POST", credentials: "include" });
}

export async function getMe(): Promise<Me | null> {
  const res = await fetch("/api/auth/me", { credentials: "include" });
  if (res.status === 401) {
    return null;
  }
  return handle<Me>(res);
}

export async function getResidents(): Promise<ResidentSummary[]> {
  const res = await fetch("/api/residents", { credentials: "include" });
  return handle<ResidentSummary[]>(res);
}

export async function createResident(
  fullName: string,
  dateOfBirth: string,
  room: string,
): Promise<ResidentSummary> {
  const res = await fetch("/api/residents", {
    method: "POST",
    credentials: "include",
    headers: jsonHeaders,
    body: JSON.stringify({ fullName, dateOfBirth, room: room || null }),
  });
  return handle<ResidentSummary>(res);
}

export async function updateResident(
  id: string,
  fullName: string,
  dateOfBirth: string,
  room: string,
): Promise<ResidentSummary> {
  const res = await fetch(`/api/residents/${id}`, {
    method: "PUT",
    credentials: "include",
    headers: jsonHeaders,
    body: JSON.stringify({ fullName, dateOfBirth, room: room || null }),
  });
  return handle<ResidentSummary>(res);
}

export async function deleteResident(id: string): Promise<void> {
  const res = await fetch(`/api/residents/${id}`, {
    method: "DELETE",
    credentials: "include",
  });
  return handle<void>(res);
}

export async function getResident(id: string): Promise<ResidentDetail> {
  const res = await fetch(`/api/residents/${id}`, { credentials: "include" });
  return handle<ResidentDetail>(res);
}

export interface StaffMember {
  id: string;
  displayName: string;
  email: string;
  role: "ADMIN" | "CARER";
  linked: boolean;
}

export interface CreateStaffRequest {
  displayName: string;
  email: string;
  role: "ADMIN" | "CARER";
}

export async function getStaff(): Promise<StaffMember[]> {
  const res = await fetch("/api/staff", { credentials: "include" });
  return handle<StaffMember[]>(res);
}

export async function createStaff(req: CreateStaffRequest): Promise<StaffMember> {
  const res = await fetch("/api/staff", {
    method: "POST",
    credentials: "include",
    headers: jsonHeaders,
    body: JSON.stringify(req),
  });
  return handle<StaffMember>(res);
}

export async function addCareLog(
  residentId: string,
  category: string,
  note: string,
): Promise<CareLogEntry> {
  const res = await fetch(`/api/residents/${residentId}/care-log`, {
    method: "POST",
    credentials: "include",
    headers: jsonHeaders,
    body: JSON.stringify({ category, note }),
  });
  return handle<CareLogEntry>(res);
}

export async function updateCareLog(
  residentId: string,
  logId: string,
  category: string,
  note: string,
): Promise<CareLogEntry> {
  const res = await fetch(`/api/residents/${residentId}/care-log/${logId}`, {
    method: "PUT",
    credentials: "include",
    headers: jsonHeaders,
    body: JSON.stringify({ category, note }),
  });
  return handle<CareLogEntry>(res);
}

export async function deleteCareLog(
  residentId: string,
  logId: string,
): Promise<void> {
  const res = await fetch(`/api/residents/${residentId}/care-log/${logId}`, {
    method: "DELETE",
    credentials: "include",
  });
  return handle<void>(res);
}
