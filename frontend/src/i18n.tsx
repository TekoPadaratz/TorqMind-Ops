import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';

export type Lang = 'pt' | 'en';

type Dict = Record<string, string>;

const pt: Dict = {
  'brand.subtitle': 'Sua operação sob controle.',
  'login.code.hint': 'Digite o código do seu app de autenticação.',
  'login.code.label': 'Código de verificação',
  'login.confirm': 'Confirmar',
  'login.verifying': 'Verificando...',
  'login.back': 'Voltar',
  'login.forgot.hint': 'Informe seu e-mail. Se houver conta, enviaremos um link para redefinir a senha.',
  'login.email': 'E-mail',
  'login.forgot.sent': 'Se o e-mail existir, enviamos as instruções.',
  'login.send': 'Enviar link',
  'login.sending': 'Enviando...',
  'login.user': 'Usuário',
  'login.user.placeholder': 'seu usuário',
  'login.password': 'Senha',
  'login.password.placeholder': 'sua senha',
  'login.enter': 'Entrar',
  'login.entering': 'Entrando...',
  'login.forgot': 'Esqueci minha senha',
  'login.invalidCode': 'Código inválido',
  'login.failed': 'Falha no login',
  'login.version': 'versão',
  'nav.radar': 'Radar',
  'nav.routines': 'Rotinas',
  'nav.occurrences': 'Ocorrências',
  'nav.admin': 'Gestão',
  'header.password': 'Senha',
  'header.logout': 'Sair',
  'header.notifications': 'Avisos',
  'header.closeNotifications': 'Fechar avisos',
  'dashboard.needsAttention': 'Existem pontos que precisam da sua atenção.',
  'dashboard.allGood': 'Operação em dia. Nada crítico agora.',
  'dashboard.pending': 'Tarefas pendentes',
  'dashboard.inProgress': 'Em andamento',
  'dashboard.late': 'Tarefas atrasadas',
  'dashboard.occOpen': 'Ocorrências abertas',
  'dashboard.awaiting': 'Aguardando validação',
  'dashboard.indicators': 'Indicadores',
  'dashboard.onTimeRate': 'Conclusão no prazo',
  'dashboard.completedTotal': 'Concluídas (total)',
  'dashboard.agingTitle': 'Atrasos por tempo',
  'dashboard.branchRanking': 'Ranking por filial (atrasos)',
  'dashboard.lateAbbr': 'atrasadas',
  'dashboard.openAbbr': 'abertas',
  'dashboard.openOccurrences': 'Ocorrências abertas',
  'dashboard.noOpenOcc': 'Nenhuma ocorrência aberta.',
  'dashboard.lateTasks': 'Tarefas atrasadas',
  'dashboard.noLateTasks': 'Nenhuma tarefa atrasada.',
  'dashboard.task': 'Tarefa',
  'dashboard.reportTitle': 'Relatório do período (PDF)',
  'dashboard.from': 'De',
  'dashboard.to': 'Até',
  'dashboard.downloadReport': 'Baixar relatório (PDF)',
  'dashboard.reportHint': 'Rotinas agendadas e ocorrências abertas no período, com indicadores e atrasos.',
  'dashboard.loading': 'Carregando...',
  'notifications.title': 'Avisos',
  'notifications.close': 'Fechar',
  'notifications.empty': 'Nenhum aviso por aqui.',
  'notifications.new': 'novo',
  'account.language': 'Idioma',
  'account.language.hint': 'Escolha o idioma da interface.'
};

const en: Dict = {
  'brand.subtitle': 'Your operation under control.',
  'login.code.hint': 'Enter the code from your authenticator app.',
  'login.code.label': 'Verification code',
  'login.confirm': 'Confirm',
  'login.verifying': 'Verifying...',
  'login.back': 'Back',
  'login.forgot.hint': 'Enter your email. If an account exists, we will send a link to reset the password.',
  'login.email': 'Email',
  'login.forgot.sent': 'If the email exists, we sent the instructions.',
  'login.send': 'Send link',
  'login.sending': 'Sending...',
  'login.user': 'Username',
  'login.user.placeholder': 'your username',
  'login.password': 'Password',
  'login.password.placeholder': 'your password',
  'login.enter': 'Sign in',
  'login.entering': 'Signing in...',
  'login.forgot': 'Forgot my password',
  'login.invalidCode': 'Invalid code',
  'login.failed': 'Login failed',
  'login.version': 'version',
  'nav.radar': 'Radar',
  'nav.routines': 'Routines',
  'nav.occurrences': 'Occurrences',
  'nav.admin': 'Admin',
  'header.password': 'Password',
  'header.logout': 'Sign out',
  'header.notifications': 'Notifications',
  'header.closeNotifications': 'Close notifications',
  'dashboard.needsAttention': 'There are items that need your attention.',
  'dashboard.allGood': 'Operation on track. Nothing critical right now.',
  'dashboard.pending': 'Pending tasks',
  'dashboard.inProgress': 'In progress',
  'dashboard.late': 'Overdue tasks',
  'dashboard.occOpen': 'Open occurrences',
  'dashboard.awaiting': 'Awaiting validation',
  'dashboard.indicators': 'Indicators',
  'dashboard.onTimeRate': 'On-time completion',
  'dashboard.completedTotal': 'Completed (total)',
  'dashboard.agingTitle': 'Overdue by age',
  'dashboard.branchRanking': 'Branch ranking (overdue)',
  'dashboard.lateAbbr': 'overdue',
  'dashboard.openAbbr': 'open',
  'dashboard.openOccurrences': 'Open occurrences',
  'dashboard.noOpenOcc': 'No open occurrences.',
  'dashboard.lateTasks': 'Overdue tasks',
  'dashboard.noLateTasks': 'No overdue tasks.',
  'dashboard.task': 'Task',
  'dashboard.reportTitle': 'Period report (PDF)',
  'dashboard.from': 'From',
  'dashboard.to': 'To',
  'dashboard.downloadReport': 'Download report (PDF)',
  'dashboard.reportHint': 'Scheduled routines and occurrences opened in the period, with indicators and overdue items.',
  'dashboard.loading': 'Loading...',
  'notifications.title': 'Notifications',
  'notifications.close': 'Close',
  'notifications.empty': 'No notifications here.',
  'notifications.new': 'new',
  'account.language': 'Language',
  'account.language.hint': 'Choose the interface language.'
};

const DICTS: Record<Lang, Dict> = { pt, en };

type Ctx = {
  lang: Lang;
  setLang: (l: Lang) => void;
  t: (key: string, vars?: Record<string, string | number>) => string;
};

const I18nContext = createContext<Ctx>({ lang: 'pt', setLang: () => undefined, t: (k) => k });

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const [lang, setLangState] = useState<Lang>(() => {
    const saved = localStorage.getItem('torqmind.lang');
    return saved === 'en' ? 'en' : 'pt';
  });

  useEffect(() => {
    document.documentElement.lang = lang === 'pt' ? 'pt-BR' : 'en';
  }, [lang]);

  function setLang(l: Lang) {
    localStorage.setItem('torqmind.lang', l);
    setLangState(l);
  }

  const t = useMemo(() => {
    return (key: string, vars?: Record<string, string | number>) => {
      let s = DICTS[lang][key] ?? DICTS.pt[key] ?? key;
      if (vars) {
        for (const k of Object.keys(vars)) {
          s = s.replace(new RegExp(`\\{${k}\\}`, 'g'), String(vars[k]));
        }
      }
      return s;
    };
  }, [lang]);

  return <I18nContext.Provider value={{ lang, setLang, t }}>{children}</I18nContext.Provider>;
}

export function useI18n(): Ctx {
  return useContext(I18nContext);
}
