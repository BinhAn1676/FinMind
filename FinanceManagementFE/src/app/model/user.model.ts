
export class User{

  public id: number;
  public name: string;
  public username: string;
  public fullName: string;
  public mobileNumber: string;
  public email : string;
  public password: string;
  public role : string;
  public statusCd: string;
  public statusMsg : string;
  public authStatus : string;
  public avatar?: string;
  public avatarFileId?: string;
  public bankToken?: string;
  public firstName?: string;
  public lastName?: string;
  public phone?: string;
  public dateOfBirth?: string;
  public address?: string;
  public bio?: string;

  constructor(id?: number, name?: string, username?: string, fullName?: string, mobileNumber?: string, email?: string, password?: string, role?: string,
      statusCd?: string, statusMsg?: string, authStatus?: string, avatar?: string, avatarFileId?: string, bankToken?: string, firstName?: string, 
      lastName?: string, phone?: string, dateOfBirth?: string, address?: string, bio?: string){
        this.id = id || 0;
        this.name = name || '';
        this.username = username || '';
        this.fullName = fullName || '';
        this.mobileNumber = mobileNumber || '';
        this.email = email || '';
        this.password = password || '';
        this.role = role || '';
        this.statusCd = statusCd || '';
        this.statusMsg = statusMsg || '';
        this.authStatus = authStatus || '';
        this.avatar = avatar || '';
        this.avatarFileId = avatarFileId || '';
        this.bankToken = bankToken || '';
        this.firstName = firstName || '';
        this.lastName = lastName || '';
        this.phone = phone || '';
        this.dateOfBirth = dateOfBirth || '';
        this.address = address || '';
        this.bio = bio || '';
  }

}
