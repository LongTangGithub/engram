'use client';

import { useEffect, useState } from 'react';
import { fetchDashboard, type DashboardView } from '../../lib/api';
import DashboardPage from '../../components/DashboardPage';

const DEMO_USER_ID = '00000000-0000-0000-0000-000000000001';

export default function Dashboard() {
  const [view, setView] = useState<DashboardView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboard(DEMO_USER_ID)
      .then(setView)
      .catch((e: Error) => setError(e.message));
  }, []);

  if (error) return <p className="p-8 text-red-500">Failed to load dashboard: {error}</p>;
  if (!view)  return <p className="p-8 text-gray-400">Loading…</p>;

  return <DashboardPage dashboard={view} />;
}
