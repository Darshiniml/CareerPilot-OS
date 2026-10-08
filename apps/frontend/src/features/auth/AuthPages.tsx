import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { Plane } from 'lucide-react';
import { authApi } from '../../api/endpoints';
import { startSession } from '../../store/session';
import { toast } from '../../store/toast';
import { Button, ErrorState, Input, Notice } from '../../components/ui';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function AuthLayout({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  useEffect(() => {
    document.title = `${title} · CareerPilot`;
  }, [title]);
  return (
    <div className="flex min-h-screen items-center justify-center bg-bg px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center text-center">
          <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-fg">
            <Plane className="h-5 w-5" aria-hidden />
          </div>
          <h1 className="text-xl font-semibold text-fg">{title}</h1>
          <p className="mt-1 text-sm text-fg-muted">{subtitle}</p>
        </div>
        <div className="rounded-2xl border border-border bg-surface p-6 shadow-card">{children}</div>
        <p className="mt-4 text-center text-sm text-fg-muted">{footer}</p>
      </div>
    </div>
  );
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/dashboard';
  const registered = (location.state as { registered?: boolean } | null)?.registered;
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState(false);

  const login = useMutation({
    mutationFn: () => authApi.login(email.trim(), password),
    onSuccess: (res) => {
      startSession(res.user, res.accessToken, res.refreshToken);
      navigate(from.startsWith('/login') ? '/dashboard' : from, { replace: true });
    },
  });

  const emailError = touched && !EMAIL_RE.test(email.trim()) ? 'Enter a valid email address' : null;
  const passwordError = touched && !password ? 'Enter your password' : null;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (!EMAIL_RE.test(email.trim()) || !password) return;
    login.mutate();
  };

  return (
    <AuthLayout
      title="Sign in to CareerPilot"
      subtitle="Your AI-assisted job search workspace"
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="font-medium text-primary hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        {registered && <Notice tone="success" title="Account created">Sign in with your new credentials.</Notice>}
        <Input label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} error={emailError} required />
        <Input
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={passwordError}
          required
        />
        {login.isError && <ErrorState error={login.error} compact />}
        <Button type="submit" className="w-full" loading={login.isPending}>
          Sign in
        </Button>
      </form>
    </AuthLayout>
  );
}

export function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '', confirm: '' });
  const [touched, setTouched] = useState(false);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  const errors = {
    firstName: !form.firstName.trim() ? 'First name is required' : null,
    lastName: !form.lastName.trim() ? 'Last name is required' : null,
    email: !EMAIL_RE.test(form.email.trim()) ? 'Enter a valid email address' : null,
    password: form.password.length < 8 ? 'Use at least 8 characters' : null,
    confirm: form.confirm !== form.password ? 'Passwords do not match' : null,
  };
  const valid = Object.values(errors).every((x) => !x);

  const register = useMutation({
    mutationFn: () =>
      authApi.register({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        password: form.password,
      }),
    onSuccess: () => {
      toast.success('Account created', 'You can now sign in.');
      navigate('/login', { replace: true, state: { registered: true } });
    },
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (valid) register.mutate();
  };
  const err = (k: keyof typeof errors) => (touched ? errors[k] : null);

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Start by uploading a resume after you sign in"
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-primary hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input label="First name" autoComplete="given-name" value={form.firstName} onChange={set('firstName')} error={err('firstName')} required />
          <Input label="Last name" autoComplete="family-name" value={form.lastName} onChange={set('lastName')} error={err('lastName')} required />
        </div>
        <Input label="Email" type="email" autoComplete="email" value={form.email} onChange={set('email')} error={err('email')} required />
        <Input
          label="Password"
          type="password"
          autoComplete="new-password"
          value={form.password}
          onChange={set('password')}
          error={err('password')}
          hint="At least 8 characters"
          required
        />
        <Input
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          value={form.confirm}
          onChange={set('confirm')}
          error={err('confirm')}
          required
        />
        {register.isError && <ErrorState error={register.error} compact />}
        <Button type="submit" className="w-full" loading={register.isPending}>
          Create account
        </Button>
      </form>
    </AuthLayout>
  );
}
