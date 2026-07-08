import { Navigate, Route, Routes } from "react-router-dom";
import { type ReactNode } from "react";
import { useAuth } from "./auth";
import Login from "./pages/Login";
import Residents from "./pages/Residents";
import ResidentDetail from "./pages/ResidentDetail";
import Staff from "./pages/Staff";

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) {
    return <p className="muted">Loading…</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

function RequireAdmin({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) {
    return <p className="muted">Loading…</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.role !== "ADMIN") {
    return <Navigate to="/residents" replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <div className="container">
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/residents"
          element={
            <RequireAuth>
              <Residents />
            </RequireAuth>
          }
        />
        <Route
          path="/residents/:id"
          element={
            <RequireAuth>
              <ResidentDetail />
            </RequireAuth>
          }
        />
        <Route
          path="/staff"
          element={
            <RequireAdmin>
              <Staff />
            </RequireAdmin>
          }
        />
        <Route path="*" element={<Navigate to="/residents" replace />} />
      </Routes>
    </div>
  );
}
